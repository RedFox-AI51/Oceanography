package com.redfoxsenpai.oceanography.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla random swimming with a sense of depth. Inside the preferred band the random point is simply kept inside the
 * band; outside it, the point is pulled up to 12 blocks toward the band so the fish heads home instead of wandering on.
 * Used by lone fish; fish in a school are steered by FishFlockGoal, which has the same depth sense built in.
 */
public class DepthSwimGoal extends RandomSwimmingGoal {

    private static final double MAX_PULL = 12.0D;

    public DepthSwimGoal(PathfinderMob mob, double speedModifier, int interval) {
        super(mob, speedModifier, interval);
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        Vec3 base = super.getPosition();
        if (base == null) return null;

        double targetY;
        if (DepthKeeping.outsideBand(mob) == 0.0D) {
            targetY = Mth.clamp(base.y, DepthKeeping.bottom(mob), DepthKeeping.top(mob));
        } else {
            double toEdge = DepthKeeping.nearestEdgeY(mob) - mob.getY();
            targetY = mob.getY() + Mth.clamp(toEdge, -MAX_PULL, MAX_PULL);
        }

        if (targetY == base.y) return base;
        return SchoolSteering.isWater(mob.level(), base.x, targetY, base.z) ? new Vec3(base.x, targetY, base.z) : base;
    }
}
