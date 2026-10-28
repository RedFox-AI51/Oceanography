package com.redfoxsenpai.oceanography.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shared swimming maths for the school goals.
 *
 * Instead of asking the pathfinder for a new path every few ticks (which makes fish jerk from waypoint to waypoint),
 * the goals compute a desired heading and this class eases the fish's actual velocity toward it every tick.
 */
final class SchoolSteering {

    private SchoolSteering() {}

    // Speeds are real blocks per tick (20 ticks = 1 second), already corrected for water drag.
    static final double CRUISE_SPEED = 0.06D;     // ~1.2 blocks/s
    static final double ALERT_SPEED = 0.10D;
    static final double REGROUP_SPEED = 0.11D;
    static final double FLEE_SPEED = 0.20D;       // ~4 blocks/s
    static final double SCATTER_SPEED = 0.26D;

    /** AbstractFish.travel() multiplies velocity by this every tick while swimming. */
    private static final double WATER_DRAG = 0.9D;
    /** Share of the gap between current and wanted velocity closed each tick. Higher = snappier turns. */
    private static final double TURN_RESPONSE = 0.22D;
    /** Without this the drag would stop the fish at ~74% of the speed we ask for. */
    private static final double DRAG_COMPENSATION =
            (1.0D - WATER_DRAG + WATER_DRAG * TURN_RESPONSE) / TURN_RESPONSE;
    private static final float MAX_TURN_DEGREES = 24.0F;

    static boolean isWater(Level level, double x, double y, double z) {
        return level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
    }

    static Vec3 rotateY(Vec3 v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    /** How many blocks (up to maxDistance) the fish can swim along dir before leaving the water. */
    static double clearDistance(Level level, Vec3 from, Vec3 dir, double maxDistance) {
        for (double d = 1.0D; d <= maxDistance; d += 1.0D) {
            if (!isWater(level, from.x + dir.x * d, from.y + dir.y * d, from.z + dir.z * d)) return d - 1.0D;
        }
        return maxDistance;
    }

    /**
     * Returns dir if the water ahead is clear, otherwise the nearest direction that is. Keeps fish off the shore, the
     * sea floor and the surface without any pathfinding. turnBias (+1 or -1) makes different fish prefer different
     * sides so a school hitting a wall doesn't all peel off the same way.
     */
    static Vec3 clearDirection(Level level, Vec3 from, Vec3 dir, double lookahead, int turnBias) {
        if (dir.lengthSqr() < 1.0E-6D) return dir;
        Vec3 forward = dir.normalize();

        double bestClear = clearDistance(level, from, forward, lookahead);
        if (bestClear >= lookahead) return forward;
        Vec3 best = forward;

        double[] turns = {0.5D, -0.5D, 1.0D, -1.0D, 1.6D, -1.6D, 2.4D, -2.4D};
        for (double turn : turns) {
            Vec3 candidate = rotateY(forward, turn * turnBias);
            double clear = clearDistance(level, from, candidate, lookahead);
            if (clear >= lookahead) return candidate;
            if (clear > bestClear) {
                bestClear = clear;
                best = candidate;
            }
        }

        double[] verticals = {-0.7D, 0.7D};
        for (double dy : verticals) {
            Vec3 candidate = new Vec3(forward.x, dy, forward.z).normalize();
            double clear = clearDistance(level, from, candidate, lookahead);
            if (clear >= lookahead) return candidate;
            if (clear > bestClear) {
                bestClear = clear;
                best = candidate;
            }
        }
        return best;
    }

    /** Gentle push away from the surface and the floor, plus the pull back toward the fish's preferred depth band. */
    static double verticalBias(Mob mob) {
        Level level = mob.level();
        double bias = 0.0D;
        if (!isWater(level, mob.getX(), mob.getEyeY() + 0.6D, mob.getZ())) bias -= 0.6D;
        if (!isWater(level, mob.getX(), mob.getY() - 0.8D, mob.getZ())) bias += 0.35D;
        return bias + DepthKeeping.steeringBias(mob);
    }

    /** Ease the fish's velocity toward heading * speed and turn its body to face where it is going. */
    static void swim(Mob mob, Vec3 heading, double speed) {
        Vec3 current = mob.getDeltaMovement();
        Vec3 wanted = heading.lengthSqr() > 1.0E-6D
                ? heading.normalize().scale(speed * DRAG_COMPENSATION)
                : Vec3.ZERO;
        Vec3 next = current.add(wanted.subtract(current).scale(TURN_RESPONSE));
        mob.setDeltaMovement(next);
        faceHeading(mob, next);
    }

    private static void faceHeading(Mob mob, Vec3 velocity) {
        if (velocity.x * velocity.x + velocity.z * velocity.z < 1.0E-4D) return;
        float targetYaw = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
        float diff = Mth.wrapDegrees(targetYaw - mob.getYRot());
        float yaw = mob.getYRot() + Mth.clamp(diff, -MAX_TURN_DEGREES, MAX_TURN_DEGREES);
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
    }
}
