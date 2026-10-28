package com.redfoxsenpai.oceanography.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;

/**
 * Biomes that no longer generate in the overworld, and what takes their place.
 *
 * Vanilla builds the overworld's biome map in code (OverworldBiomeBuilder), so it cannot be edited with a data pack.
 * Instead, {@code OverworldBiomeBuilderMixin} runs every biome the builder hands out through {@link #replace}.
 * To bring a biome back, delete its line; to pick a different stand-in, change the right-hand side.
 *
 * The three underground biomes (dripstone_caves, lush_caves, deep_dark) are not listed here: they are simply never added,
 * so caves are ordinary caves and the area below them uses whatever biome is above.
 */
public final class BiomeRemovals {

    private static final Map<String, String> REPLACEMENTS = Map.ofEntries(
            Map.entry("savanna", "plains"),
            Map.entry("savanna_plateau", "plains"),
            Map.entry("windswept_savanna", "plains"),
            Map.entry("badlands", "desert"),
            Map.entry("eroded_badlands", "desert"),
            Map.entry("wooded_badlands", "forest"),
            Map.entry("stony_peaks", "meadow"),
            Map.entry("jagged_peaks", "frozen_peaks"),
            Map.entry("windswept_hills", "plains"),
            Map.entry("windswept_gravelly_hills", "plains"),
            Map.entry("windswept_forest", "forest"));

    private BiomeRemovals() {}

    public static ResourceKey<Biome> replace(ResourceKey<Biome> biome) {
        ResourceLocation id = biome.location();
        if (!"minecraft".equals(id.getNamespace())) return biome;
        String standIn = REPLACEMENTS.get(id.getPath());
        return standIn == null
                ? biome
                : ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(standIn));
    }
}
