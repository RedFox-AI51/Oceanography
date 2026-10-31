package com.redfoxsenpai.oceanography.current;

import com.redfoxsenpai.oceanography.Oceanography;
import com.redfoxsenpai.oceanography.entity.ChumEntity;
import com.redfoxsenpai.oceanography.entity.SchoolState;
import com.redfoxsenpai.oceanography.entity.ThrownChumEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server side of the ocean currents: nudges everything floating or swimming in the sea toward the speed of the water.
 *
 * Players are NOT moved here. A player's movement is decided by their own game, so the server pushing them would be undone
 * on the next movement packet; ClientCurrents pushes the local player instead. The same goes for a boat a player is steering.
 *
 * Fish that are swimming under their own power (our school goals) already account for the current themselves, so they are
 * only carried along here when they are idle.
 */
@Mod.EventBusSubscriber(modid = Oceanography.MODID)
public final class CurrentPhysics {

    /** Share of the gap between the entity's speed and the water's speed closed each tick. Bigger = follows the water faster. */
    private static final double ITEM_COUPLING = 0.04D;
    private static final double GENERIC_COUPLING = 0.02D;     // boats, mobs, experience orbs, anything else afloat
    private static final double IDLE_FISH_COUPLING = 0.03D;

    private CurrentPhysics() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        for (Entity entity : level.getEntities().getAll()) {
            if (entity.isRemoved() || entity instanceof Player || entity.isPassenger()) continue;
            if (!entity.isInWater() || entity.onGround()) continue;
            if (entity.getControllingPassenger() instanceof Player) continue;       // that player's game drives it

            double coupling = coupling(entity, now);
            if (coupling <= 0.0D) continue;

            Vec3 water = OceanCurrents.at(level, entity.getX(), entity.getY(), entity.getZ());
            if (water == Vec3.ZERO) continue;

            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(v.x + (water.x - v.x) * coupling, v.y, v.z + (water.z - v.z) * coupling);
        }
    }

    private static double coupling(Entity entity, long now) {
        if (entity instanceof ItemEntity) return ITEM_COUPLING;
        if (entity instanceof Projectile || entity instanceof ChumEntity || entity instanceof ThrownChumEntity) return 0.0D;
        if (entity instanceof Mob mob) {
            SchoolState state = SchoolState.peek(mob);
            if (state != null) return now - state.getLastSteerTick() > 2L ? IDLE_FISH_COUPLING : 0.0D;
            return GENERIC_COUPLING;
        }
        if (entity instanceof LivingEntity) return 0.0D;          // armor stands and the like
        return GENERIC_COUPLING;
    }
}
