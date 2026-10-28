package com.redfoxsenpai.oceanography.mixin;

import com.mojang.datafixers.util.Pair;
import com.redfoxsenpai.oceanography.worldgen.BiomeRemovals;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Removes biomes from the overworld's generation. See {@link BiomeRemovals} for the list.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {

    /** Every surface biome passes through here on its way into the map; swap the removed ones for their stand-ins. */
    @ModifyVariable(method = "addSurfaceBiome", at = @At("HEAD"), argsOnly = true, remap = false)
    private ResourceKey<Biome> oceanography$replaceRemovedBiome(ResourceKey<Biome> biome) {
        return BiomeRemovals.replace(biome);
    }

    /** dripstone_caves, lush_caves and deep_dark are the only things this method adds, so skip all of it. */
    @Inject(method = "addUndergroundBiomes", at = @At("HEAD"), cancellable = true, remap = false)
    private void oceanography$noCaveBiomes(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
                                           CallbackInfo ci) {
        ci.cancel();
    }
}
