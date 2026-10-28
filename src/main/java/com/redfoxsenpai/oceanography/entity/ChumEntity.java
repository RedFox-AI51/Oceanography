package com.redfoxsenpai.oceanography.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

public class ChumEntity extends Entity {

    private static final EntityDataAccessor<Integer> EATEN_COUNT =
            SynchedEntityData.defineId(ChumEntity.class, EntityDataSerializers.INT);

    private static final int MAX_EATEN = 12;              // was 5
    private static final double FEEDING_RADIUS = 1.3D;    // ring radius fish spread around
    private static final double RISE_SPEED = 0.06D;
    private static final double SUBMERGE_OFFSET = 0.125D;

    private static final double BUBBLE_RADIUS = 1.1D;     // was 0.6
    private static final int BUBBLES_PER_TICK = 6;         // was 3

    private static final Vector3f CHUM_RED = new Vector3f(0.85F, 0.05F, 0.05F);
    private static final double TINT_MAX_RADIUS = 3.5D;    // was 2.2
    private static final double TINT_GROWTH_TICKS = 100.0D;
    private static final int TINT_PARTICLES_PER_TICK = 9;  // was 5

    private final Set<UUID> claimedFish = new HashSet<>();
    private final Map<UUID, Integer> reservedSlots = new HashMap<>();
    private final ArrayDeque<Integer> freedSlots = new ArrayDeque<>();
    private int nextSlotIndex = 0;

    private boolean hasSurfaced = false;

    public ChumEntity(EntityType<? extends ChumEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(EATEN_COUNT, 0);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            spawnBubbleParticles();
            spawnRedTintParticles();
            return;
        }

        if (!hasSurfaced) {
            BlockPos feetPos = blockPosition();
            FluidState fluidAtFeet = level().getFluidState(feetPos);

            if (fluidAtFeet.is(FluidTags.WATER)) {
                this.setDeltaMovement(0, RISE_SPEED, 0);
                this.move(MoverType.SELF, this.getDeltaMovement());
            } else {
                this.setPos(getX(), feetPos.getY() - SUBMERGE_OFFSET, getZ());
                this.setDeltaMovement(0, 0, 0);
                hasSurfaced = true;
            }
        }

        if (getEatenCount() >= MAX_EATEN) {
            this.discard();
        }
    }

    private void spawnBubbleParticles() {
        for (int i = 0; i < BUBBLES_PER_TICK; i++) {
            double offsetX = (random.nextDouble() - 0.5) * BUBBLE_RADIUS * 2;
            double offsetZ = (random.nextDouble() - 0.5) * BUBBLE_RADIUS * 2;
            double offsetY = -random.nextDouble() * 0.5;

            level().addParticle(ParticleTypes.BUBBLE_POP,
                    getX() + offsetX, getY() + offsetY, getZ() + offsetZ,
                    0, 0.02, 0);
        }
    }

    private void spawnRedTintParticles() {
        double growth = Math.min(1.0, this.tickCount / TINT_GROWTH_TICKS);
        double currentRadius = TINT_MAX_RADIUS * growth;
        DustParticleOptions dust = new DustParticleOptions(CHUM_RED, 1.6F);

        for (int i = 0; i < TINT_PARTICLES_PER_TICK; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = currentRadius * Math.sqrt(random.nextDouble());
            double offsetX = Math.cos(angle) * dist;
            double offsetZ = Math.sin(angle) * dist;
            double offsetY = -random.nextDouble() * 0.6;

            level().addParticle(dust,
                    getX() + offsetX, getY() + offsetY, getZ() + offsetZ,
                    0, -0.01, 0);
        }

        int aboveCount = Math.max(1, TINT_PARTICLES_PER_TICK / 2);
        for (int i = 0; i < aboveCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = currentRadius * 0.7 * Math.sqrt(random.nextDouble());
            double offsetX = Math.cos(angle) * dist;
            double offsetZ = Math.sin(angle) * dist;
            double offsetY = random.nextDouble() * 0.15;

            level().addParticle(dust,
                    getX() + offsetX, getY() + offsetY, getZ() + offsetZ,
                    0, 0.0, 0);
        }
    }

    /** Reserve a feeding slot around the chum's edge. Returns the slot index, or -1 if full. */
    public int reserveSlot(Mob fish) {
        Integer existing = reservedSlots.get(fish.getUUID());
        if (existing != null) return existing;
        if (!freedSlots.isEmpty()) {
            int reused = freedSlots.pop();
            reservedSlots.put(fish.getUUID(), reused);
            return reused;
        }
        if (nextSlotIndex >= MAX_EATEN) return -1;

        int slot = nextSlotIndex++;
        reservedSlots.put(fish.getUUID(), slot);
        return slot;
    }

    /** A fish that was scared off (or otherwise gave up) hands its slot back so another fish can use it. */
    public void releaseSlot(Mob fish) {
        Integer slot = reservedSlots.remove(fish.getUUID());
        if (slot != null && !claimedFish.contains(fish.getUUID())) freedSlots.push(slot);
    }

    /** World position of a given feeding slot, arranged evenly around the chum. */
    public Vec3 getSlotPosition(int slotIndex) {
        double angle = (2 * Math.PI * slotIndex) / MAX_EATEN;
        double x = getX() + Math.cos(angle) * FEEDING_RADIUS;
        double z = getZ() + Math.sin(angle) * FEEDING_RADIUS;
        return new Vec3(x, getY(), z);
    }

    public boolean tryClaim(Mob fish) {
        if (getEatenCount() >= MAX_EATEN) return false;
        if (claimedFish.contains(fish.getUUID())) return false;
        claimedFish.add(fish.getUUID());
        setEatenCount(getEatenCount() + 1);
        return true;
    }

    public int getEatenCount() {
        return this.entityData.get(EATEN_COUNT);
    }

    private void setEatenCount(int count) {
        this.entityData.set(EATEN_COUNT, count);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setEatenCount(tag.getInt("EatenCount"));
        hasSurfaced = tag.getBoolean("HasSurfaced");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("EatenCount", getEatenCount());
        tag.putBoolean("HasSurfaced", hasSurfaced);
    }
}