package com.redfoxsenpai.oceanography.world;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.Set;

/**
 * Stops the mobs listed in the entity tag {@code oceanography:no_natural_spawn} from appearing on their own.
 *
 * The biome modifier {@code remove_blocked_mob_spawns} already takes them out of every biome's spawn list, which covers the
 * everyday case cheaply. This catches everything that does not come from a biome list: dungeon and trial spawners,
 * structures (outposts, mansions, huts), patrols and raids, phantoms, zombie reinforcements, skeleton traps and so on.
 *
 * Spawn eggs, /summon, dispensers, buckets, breeding and conversions are left alone so you can still create any mob on
 * purpose. To change which mobs are blocked, edit the tag files under data/oceanography/tags/entity_type/ - no code change.
 */
@Mod.EventBusSubscriber(modid = Oceanography.MODID)
public final class MobSpawnBlocker {

    public static final TagKey<EntityType<?>> BLOCKED = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Oceanography.MODID, "no_natural_spawn"));

    /** Every way the world itself can decide to put a mob down. */
    private static final Set<MobSpawnType> AUTOMATIC = EnumSet.of(
            MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.STRUCTURE,
            MobSpawnType.SPAWNER, MobSpawnType.TRIAL_SPAWNER, MobSpawnType.PATROL, MobSpawnType.EVENT,
            MobSpawnType.REINFORCEMENT, MobSpawnType.JOCKEY, MobSpawnType.TRIGGERED, MobSpawnType.MOB_SUMMONED);

    private MobSpawnBlocker() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        boolean blockedMob = event.getEntity().getType().is(BLOCKED)
                || event.getEntity().getType() == EntityType.ENDERMAN;
        if (AUTOMATIC.contains(event.getSpawnType()) && blockedMob) {
            event.setSpawnCancelled(true);
        }
    }
}
