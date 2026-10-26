package com.redfoxsenpai.oceanography.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * "Below sea level is the default fluid, above it is air", at every height.
 *
 * Vanilla's picker returns lava for anything empty below y = -54 regardless of the dimension's sea level. That is fine
 * for a world that stops at y = -64, but in a world that goes down to -448 it turns the bottom of every ocean into lava.
 * See {@code NoiseBasedChunkGeneratorMixin} for where this gets used.
 */
public final class WaterFluidPicker implements Aquifer.FluidPicker {

    private final Aquifer.FluidStatus status;

    public WaterFluidPicker(int seaLevel, BlockState defaultFluid) {
        this.status = new Aquifer.FluidStatus(seaLevel, defaultFluid);
    }

    @Override
    public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
        return status;
    }
}
