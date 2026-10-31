package com.redfoxsenpai.oceanography.world;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wandering traders are spawned by a special spawner, not from a biome's spawn list, so the biome modifier cannot remove them.
 * Vanilla has a game rule for exactly this (doTraderSpawning); this switches it off every time a world starts.
 *
 * The traders and their llamas are also in the entity tag oceanography:no_natural_spawn/other, so MobSpawnBlocker stops them
 * if anything else tries to spawn them. Spawn eggs and /summon still work.
 */
@Mod.EventBusSubscriber(modid = Oceanography.MODID)
public final class TraderRemoval {

    private TraderRemoval() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        event.getServer().getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, event.getServer());
    }
}
