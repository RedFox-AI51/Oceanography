package com.redfoxsenpai.oceanography.mixin;

import com.redfoxsenpai.oceanography.worldgen.WaterFluidPicker;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla hardcodes "anything empty below y = -54 is lava" in {@code NoiseBasedChunkGenerator.createFluidPicker}.
 * That is not in any data file, so the only way to change it is in code.
 *
 * Settings that stay within vanilla's height (min_y of -64 or higher) are left completely alone, so the Nether, the End
 * and any vanilla-height overworld behave exactly as before. Only noise settings that reach below -64 get water.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {

    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true, remap = false)
    private static void oceanography$waterInsteadOfDeepLava(NoiseGeneratorSettings settings,
                                                           CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        if (settings.noiseSettings().minY() >= -64) return;
        cir.setReturnValue(new WaterFluidPicker(settings.seaLevel(), settings.defaultFluid()));
    }
}
