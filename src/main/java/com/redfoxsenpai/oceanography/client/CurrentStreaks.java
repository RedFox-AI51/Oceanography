package com.redfoxsenpai.oceanography.client;

import com.redfoxsenpai.oceanography.current.ModParticles;
import com.redfoxsenpai.oceanography.current.CurrentField;
import com.redfoxsenpai.oceanography.current.OceanCurrents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Makes the ocean currents visible by seeding streaks of light around the local player.
 *
 * Streaks appear only where there is a current worth showing, so still water stays clear. They are seeded a little
 * upstream of the player, which keeps the cloud of streaks centred on you instead of being carried away downstream.
 *
 * Holding a compass in either hand makes the effect denser and wider - a "current meter" for finding the strong streams.
 *
 * To switch the effect off entirely, set ENABLED to false.
 */
public final class CurrentStreaks {

    public static final boolean ENABLED = true;

    private static final int BASE_HEADS_PER_TICK = 12;
    private static final int HEAD_BONUS_PER_TICK = 18;
    private static final int BASE_HEADS_PER_TICK_WITH_COMPASS = 18;
    private static final int HEAD_BONUS_PER_TICK_WITH_COMPASS = 22;
    private static final double RADIUS = 9.0D;
    private static final double RADIUS_WITH_COMPASS = 12.0D;
    private static final double SHELL_RADIUS = 1.8D;
    private static final int SHELL_POINTS = 16;

    private CurrentStreaks() {}

    public static void spawnAround(Player player) {
        if (!ENABLED) return;

        Level level = player.level();
        boolean compass = player.getMainHandItem().is(Items.COMPASS) || player.getOffhandItem().is(Items.COMPASS);
        double radius = compass ? RADIUS_WITH_COMPASS : RADIUS;
        RandomSource random = player.getRandom();

        // seed upstream of the player so the streaks stay centred on them as they are carried downstream
        Vec3 here = OceanCurrents.at(level, player.getX(), player.getEyeY(), player.getZ());
        double flow = Math.hypot(here.x, here.z);
        double shift = Math.min(12.0D, flow * CurrentMoteParticle.STREAK_SPEED * CurrentMoteParticle.HEAD_LIFETIME * 0.5D);
        double upstreamX = flow > 1.0E-9D ? -here.x / flow : 0.0D;
        double upstreamZ = flow > 1.0E-9D ? -here.z / flow : 0.0D;
        double threshold = CurrentMoteParticle.fastCurrentThreshold();
        double thresholdRange = CurrentField.MAX_SPEED - threshold;
        double strength = thresholdRange > 0.0D
                ? Math.max(0.0D, Math.min(1.0D, (flow - threshold) / thresholdRange))
                : 1.0D;
        int baseHeads = compass ? BASE_HEADS_PER_TICK_WITH_COMPASS : BASE_HEADS_PER_TICK;
        int headBonus = compass ? HEAD_BONUS_PER_TICK_WITH_COMPASS : HEAD_BONUS_PER_TICK;
        int heads = baseHeads + (int) Math.ceil(strength * headBonus);

        for (int i = 0; i < heads; i++) {
            double x = player.getX() + upstreamX * shift + (random.nextDouble() * 2.0D - 1.0D) * radius;
            double y = player.getEyeY() + (random.nextDouble() * 2.0D - 1.0D) * radius * 0.6D;
            double z = player.getZ() + upstreamZ * shift + (random.nextDouble() * 2.0D - 1.0D) * radius;

            if (!level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) continue;
            Vec3 water = OceanCurrents.at(level, x, y, z);
            if (Math.hypot(water.x, water.z) < threshold) continue;

            // a head is a particle spawned WITH velocity; the velocity itself is ignored, the particle follows the field
            level.addParticle(ModParticles.CURRENT_MOTE.get(), x, y, z, water.x, 0.0D, water.z);

            // A translucent particle shell marks the outer edge of each strong current tube.
            if (i % 4 == 0) {
                for (int point = 0; point < SHELL_POINTS; point++) {
                    double phi = 2.0D * Math.PI * point / SHELL_POINTS;
                    double shellPhase = random.nextDouble() * 0.35D;
                    double horizontalRadius = SHELL_RADIUS * (0.75D + 0.25D * Math.cos(shellPhase));
                    double shellX = x + Math.cos(phi) * horizontalRadius;
                    double shellY = y + Math.sin(phi) * SHELL_RADIUS;
                    double shellZ = z + Math.sin(shellPhase) * horizontalRadius;
                    if (!level.getFluidState(BlockPos.containing(shellX, shellY, shellZ)).is(FluidTags.WATER)) continue;
                    level.addParticle(ModParticles.CURRENT_MOTE.get(), shellX, shellY, shellZ, water.x, 1.0D, water.z);
                }
            }
        }
    }
}
