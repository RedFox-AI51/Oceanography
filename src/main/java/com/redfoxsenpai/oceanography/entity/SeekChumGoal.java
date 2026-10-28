package com.redfoxsenpai.oceanography.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class SeekChumGoal extends Goal {

    private static final double SEARCH_RADIUS = 12.0D; // was 10
    private static final double EAT_DISTANCE = 0.8D;

    private static final double LEAP_TRIGGER_RANGE = 3.0D;
    private static final double LEAP_CHANCE_PER_TICK = 0.04D;
    private static final int LEAP_COOLDOWN_TICKS = 60;
    private static final int LEAP_DURATION_TICKS = 12;
    private static final double LEAP_VERTICAL_SPEED = 0.55D;
    private static final double LEAP_HORIZONTAL_SPEED = 0.25D;

    private final Mob fish;
    private final double speedModifier;
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int FULL_BELLY_MIN_TICKS = 600;    // a fish that just ate loses interest for 30-60 seconds
    private static final int FULL_BELLY_EXTRA_TICKS = 600;

    private ChumEntity targetChum;
    private int slotIndex = -1;
    private boolean ate;                // this run succeeded
    private long fullUntil;             // game time until which the fish ignores chum
    private long nextScan;

    private int leapCooldown;
    private int leapTicksRemaining;

    public SeekChumGoal(Mob fish, double speedModifier) {
        this.fish = fish;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        long now = fish.level().getGameTime();
        if (now < fullUntil || now < nextScan) return false;
        nextScan = now + SCAN_INTERVAL_TICKS + fish.getRandom().nextInt(SCAN_INTERVAL_TICKS);

        AABB searchBox = fish.getBoundingBox().inflate(SEARCH_RADIUS);
        List<ChumEntity> chumList = fish.level().getEntitiesOfClass(ChumEntity.class, searchBox,
                chum -> chum.getEatenCount() < 12);

        if (chumList.isEmpty()) return false;

        for (ChumEntity chum : chumList) {
            int slot = chum.reserveSlot(fish);
            if (slot != -1) {
                targetChum = chum;
                slotIndex = slot;
                return true;
            }
        }
        return false; // every nearby chum is fully claimed
    }

    @Override
    public boolean canContinueToUse() {
        return targetChum != null && targetChum.isAlive() && targetChum.getEatenCount() < 12 && !ate;
    }

    @Override
    public void start() {
        ate = false;
        moveTowardSlot();
    }

    @Override
    public void tick() {
        if (targetChum == null) return;

        if (leapCooldown > 0) leapCooldown--;

        if (leapTicksRemaining > 0) {
            leapTicksRemaining--;
            return;
        }

        moveTowardSlot();

        Vec3 slotPos = targetChum.getSlotPosition(slotIndex);
        double distSqr = fish.position().distanceToSqr(slotPos);

        if (distSqr < EAT_DISTANCE * EAT_DISTANCE) {
            if (targetChum.tryClaim(fish)) {
                ate = true;
                fullUntil = fish.level().getGameTime() + FULL_BELLY_MIN_TICKS + fish.getRandom().nextInt(FULL_BELLY_EXTRA_TICKS);
            }
            return;
        }

        maybeLeap(distSqr, slotPos);
    }

    private void moveTowardSlot() {
        Vec3 slotPos = targetChum.getSlotPosition(slotIndex);
        fish.getNavigation().moveTo(slotPos.x, slotPos.y, slotPos.z, speedModifier);
    }

    private void maybeLeap(double distSqr, Vec3 slotPos) {
        if (leapCooldown > 0) return;
        if (distSqr > LEAP_TRIGGER_RANGE * LEAP_TRIGGER_RANGE) return;
        if (!fish.isInWater()) return;
        if (slotPos.y <= fish.getY() + 0.1) return;
        if (fish.getRandom().nextDouble() > LEAP_CHANCE_PER_TICK) return;

        Vec3 toTarget = slotPos.subtract(fish.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z);
        if (horizontal.lengthSqr() > 1.0E-4) {
            horizontal = horizontal.normalize();
        }

        fish.setDeltaMovement(
                horizontal.x * LEAP_HORIZONTAL_SPEED,
                LEAP_VERTICAL_SPEED,
                horizontal.z * LEAP_HORIZONTAL_SPEED
        );
        fish.hasImpulse = true;

        leapTicksRemaining = LEAP_DURATION_TICKS;
        leapCooldown = LEAP_COOLDOWN_TICKS;

        if (fish.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SPLASH,
                    fish.getX(), fish.getY() + 0.2, fish.getZ(),
                    8, 0.2, 0.1, 0.2, 0.02);
            serverLevel.playSound(null, fish.blockPosition(), SoundEvents.GENERIC_SPLASH,
                    SoundSource.NEUTRAL, 0.3F, 1.0F + (fish.getRandom().nextFloat() - 0.5F) * 0.2F);
        }
    }

    @Override
    public void stop() {
        // scared off before eating: give the slot back so another fish can have it
        if (targetChum != null && !ate) targetChum.releaseSlot(fish);
        targetChum = null;
        slotIndex = -1;
        leapTicksRemaining = 0;
    }
}