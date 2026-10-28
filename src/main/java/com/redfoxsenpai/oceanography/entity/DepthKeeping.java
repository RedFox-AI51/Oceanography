package com.redfoxsenpai.oceanography.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * Gives fish a preferred depth so they live in the upper water instead of drifting to the sea floor.
 *
 * Nothing in vanilla's fish AI cares about depth: random swimming picks targets a few blocks up or down with no
 * preference, and fish also carry a small built-in downward drift. In a 40-block ocean you never notice. In a 460-block
 * ocean, almost all of the water is deep, so a fish that wanders without a preference ends up deep almost every time.
 *
 * Each fish gets a "band": from just under the surface down to a personal depth between PREFERRED_DEPTH_MIN and
 * PREFERRED_DEPTH_MAX. Inside the band the fish swims freely. Outside it a gentle spring pulls it back, which also
 * cancels any constant sink or float, whatever its cause. The spring is deliberately weak, so goals (feeding, fleeing,
 * schooling) still win whenever they are running.
 */
public final class DepthKeeping {

    private DepthKeeping() {}

    /** Blocks below the surface that the BOTTOM of a fish's band sits at. Each fish gets its own value in this range. */
    static final double PREFERRED_DEPTH_MIN = 20.0D;
    static final double PREFERRED_DEPTH_MAX = 45.0D;
    /** The top of the band stays this far under the surface so fish don't hug it. */
    static final double SURFACE_MARGIN = 1.0D;
    /** Fish may spawn from this deep up to just under the surface (see Oceanography.java). */
    public static final double SPAWN_DEPTH = 40.0D;

    /** Upward/downward push (blocks per tick, per block outside the band). Kept low enough to be overdamped in water. */
    private static final double SPRING_GAIN = 0.002D;
    private static final double SPRING_MAX = 0.012D;

    static double top(Mob mob) {
        return mob.level().getSeaLevel() - SURFACE_MARGIN;
    }

    static double bottom(Mob mob) {
        double unit = unitHash(mob);
        return mob.level().getSeaLevel() - (PREFERRED_DEPTH_MIN + unit * (PREFERRED_DEPTH_MAX - PREFERRED_DEPTH_MIN));
    }

    /** A stable 0..1 number per fish, so a fish keeps the same preferred depth for its whole life with nothing to save. */
    private static double unitHash(Mob mob) {
        long h = mob.getUUID().getLeastSignificantBits() ^ mob.getUUID().getMostSignificantBits();
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return ((h >>> 8) & 0xFFFFL) / 65535.0D;
    }

    /** 0 inside the band, negative = that many blocks BELOW it, positive = that many blocks ABOVE it. */
    static double outsideBand(Mob mob) {
        double y = mob.getY();
        double bottom = bottom(mob);
        if (y < bottom) return y - bottom;
        double top = top(mob);
        if (y > top) return y - top;
        return 0.0D;
    }

    /** Called every tick for a swimming fish: a weak pull back toward the band. */
    static void applySpring(Mob mob) {
        double out = outsideBand(mob);
        if (out == 0.0D) return;
        double push = Math.min(SPRING_MAX, Math.abs(out) * SPRING_GAIN) * -Math.signum(out);
        mob.setDeltaMovement(mob.getDeltaMovement().add(0.0D, push, 0.0D));
    }

    /** Vertical component for steering: positive = go up, negative = go down, 0 inside the band. Grows with distance. */
    static double steeringBias(Mob mob) {
        double out = outsideBand(mob);
        return out == 0.0D ? 0.0D : Mth.clamp(-out / 8.0D, -2.0D, 2.0D);
    }

    /** The nearest edge of the band, for fish that are outside it. */
    static double nearestEdgeY(Mob mob) {
        double out = outsideBand(mob);
        return out == 0.0D ? mob.getY() : mob.getY() - out;
    }
}
