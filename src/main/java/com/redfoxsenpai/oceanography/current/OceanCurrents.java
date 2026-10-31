package com.redfoxsenpai.oceanography.current;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft-facing access to the ocean current field (see CurrentField for how the flow is made).
 *
 * Currents exist only in ocean biomes and only at or below the sea surface. Everything else gets no current at all.
 * The same call works on the client and on the server and gives the same answer, which is what lets the server push fish,
 * items and boats while each player's own game pushes that player.
 */
public final class OceanCurrents {

    private OceanCurrents() {}

    /** Water velocity at a point, in blocks per tick (horizontal only). Vec3.ZERO outside the ocean. */
    public static Vec3 at(Level level, double x, double y, double z) {
        int seaLevel = level.getSeaLevel();
        if (y > seaLevel + 1.0D) return Vec3.ZERO;
        if (!level.getBiome(BlockPos.containing(x, y, z)).is(BiomeTags.IS_OCEAN)) return Vec3.ZERO;

        double[] out = new double[3];          // a fresh array each call: the client and server threads both use this
        CurrentField.sample(x, y, z, level.getGameTime(), seaLevel, out);
        return new Vec3(out[0], 0.0D, out[2]);
    }
}
