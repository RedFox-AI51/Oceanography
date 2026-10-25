package com.redfoxsenpai.oceanography.entity;

import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ThrownChumEntity extends ThrowableItemProjectile {

    public ThrownChumEntity(EntityType<? extends ThrownChumEntity> type, Level level) {
        super(type, level);
    }

    public ThrownChumEntity(Level level, LivingEntity thrower) {
        super(ModEntityTypes.THROWN_CHUM.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @Override
    public void tick() {
        super.tick();

        // Stop the instant it touches water, rather than sinking to the floor
        // and waiting for a solid-block collision.
        if (!level().isClientSide && !this.isRemoved()
                && level().getFluidState(blockPosition()).is(FluidTags.WATER)) {
            BlockHitResult surfaceHit = new BlockHitResult(position(), Direction.UP, blockPosition(), false);
            onHit(surfaceHit);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (level().isClientSide || this.isRemoved()) return;

        boolean hitWater = false;

        if (result instanceof BlockHitResult blockHit) {
            hitWater = level().getFluidState(blockHit.getBlockPos()).is(FluidTags.WATER)
                    || level().getFluidState(blockHit.getBlockPos().above()).is(FluidTags.WATER);
        } else if (result instanceof EntityHitResult) {
            hitWater = level().getFluidState(blockPosition()).is(FluidTags.WATER);
        }

        if (hitWater) {
            ChumEntity chum = new ChumEntity(ModEntityTypes.CHUM.get(), level());
            chum.setPos(getX(), getY(), getZ());
            level().addFreshEntity(chum);
        }

        this.discard();
    }
}