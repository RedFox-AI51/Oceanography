package com.redfoxsenpai.oceanography.entity;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

/**
 * Lets fish hear things. Vanilla announces notable happenings as "game events" (a splash, an explosion, a block breaking);
 * this turns the loud ones into scares for every fish within earshot, fainter with distance. A loud noise makes fish bolt;
 * a faint one only makes them uneasy. Water carries sound well; noise from the air is muffled.
 *
 * To make fish hear more or less, edit the table below.
 */
@Mod.EventBusSubscriber(modid = Oceanography.MODID)
public final class FishNoise {

    /** loudness 0..1 at the source (1.0 scatters a school), radius in blocks at which it fades to nothing. */
    private record Noise(float loudness, float radius) {}

    private static final Map<String, Noise> NOISES = Map.of(
            "explode",          new Noise(1.00F, 48.0F),
            "lightning_strike", new Noise(0.90F, 40.0F),
            "splash",           new Noise(0.70F, 20.0F),
            "entity_damage",    new Noise(0.55F, 14.0F),
            "projectile_land",  new Noise(0.50F, 14.0F),
            "block_destroy",    new Noise(0.50F, 12.0F),
            "entity_die",       new Noise(0.45F, 12.0F),
            "block_place",      new Noise(0.40F, 10.0F));

    private FishNoise() {}

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ResourceLocation key = BuiltInRegistries.GAME_EVENT.getKey(event.getVanillaEvent());
        if (key == null) return;
        Noise noise = NOISES.get(key.getPath());
        if (noise == null) return;

        Entity cause = event.getCause();
        // Chum lands and fish leap all day; that is dinner, not danger.
        if (cause instanceof ThrownChumEntity || cause instanceof ChumEntity) return;
        // Fish make splashes and swim sounds constantly. Only let them cause alarm when one of them is actually hurt.
        if (cause instanceof Mob mob && SchoolState.isTracked(mob) && !"entity_damage".equals(key.getPath())) return;

        Vec3 source = event.getEventPosition();
        BlockPos sourcePos = BlockPos.containing(source);
        boolean wet = level.getFluidState(sourcePos).is(FluidTags.WATER) || level.getFluidState(sourcePos.below()).is(FluidTags.WATER);
        float loudness = wet ? noise.loudness() : noise.loudness() * 0.5F;

        List<Mob> fish = level.getEntitiesOfClass(Mob.class, new AABB(source, source).inflate(noise.radius()), SchoolState::isTracked);
        for (Mob mob : fish) {
            double dist = mob.position().distanceTo(source);
            if (dist > noise.radius()) continue;
            SchoolState state = SchoolState.peek(mob);
            if (state == null) continue;
            float falloff = (float) Math.pow(1.0D - dist / noise.radius(), 1.3D);
            state.hear(source, loudness * falloff);
        }
    }
}
