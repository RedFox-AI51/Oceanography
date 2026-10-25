package com.redfoxsenpai.oceanography.entity;

import com.redfoxsenpai.oceanography.block.FishFoodBlockEntity;
import com.redfoxsenpai.oceanography.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SeekFishFoodGoal extends Goal {

    private static final int SEARCH_RADIUS = 8;
    private static final double EAT_DISTANCE = 0.6D;

    private final Mob fish;
    private final double speedModifier;
    private BlockPos targetPos;
    private boolean satisfied;

    public SeekFishFoodGoal(Mob fish, double speedModifier) {
        this.fish = fish;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (satisfied) return false;

        BlockPos fishPos = fish.blockPosition();
        BlockPos nearest = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                fishPos.offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                fishPos.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {

            if (fish.level().getBlockState(pos).is(ModBlocks.FISH_FOOD.get())
                    && fish.level().getBlockEntity(pos) instanceof FishFoodBlockEntity be
                    && be.getCapacity() > 0) {

                double distSqr = fishPos.distSqr(pos);
                if (distSqr < bestDistSqr) {
                    bestDistSqr = distSqr;
                    nearest = pos.immutable();
                }
            }
        }

        if (nearest == null) return false;
        targetPos = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !satisfied && targetPos != null
                && fish.level().getBlockState(targetPos).is(ModBlocks.FISH_FOOD.get());
    }

    @Override
    public void start() {
        moveTowardTarget();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;
        moveTowardTarget();

        double distSqr = fish.position().distanceToSqr(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);

        if (distSqr < EAT_DISTANCE * EAT_DISTANCE
                && fish.level().getBlockEntity(targetPos) instanceof FishFoodBlockEntity be
                && be.tryEat(fish)) {

            satisfied = true;

            if (be.getCapacity() <= 0) {
                fish.level().removeBlock(targetPos, false);
                if (fish.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SPLASH,
                            targetPos.getX() + 0.5, targetPos.getY() + 0.2, targetPos.getZ() + 0.5,
                            6, 0.2, 0.05, 0.2, 0.02);
                }
            }
        }
    }

    private void moveTowardTarget() {
        fish.getNavigation().moveTo(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, speedModifier);
    }

    @Override
    public void stop() {
        targetPos = null;
    }
}