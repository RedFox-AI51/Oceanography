package com.redfoxsenpai.oceanography.current;

import com.redfoxsenpai.oceanography.Oceanography;
import com.redfoxsenpai.oceanography.client.CurrentStreaks;
import com.redfoxsenpai.oceanography.current.OceanographyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of the ocean currents: the local player (and a boat they are steering) is carried by the water.
 *
 * Vanilla does the same thing for flowing water: the push is applied by the player's own game, not the server, because the
 * client is the authority on where its own player is. Since the current is computed from the same function on both sides,
 * nothing needs to be sent over the network.
 */
@Mod.EventBusSubscriber(modid = Oceanography.MODID, value = Dist.CLIENT)
public final class ClientCurrents {

    private static final double WEAK_CURRENT_DRAG = 0.08D;
    private static final double STRONG_CURRENT_DRAG = 0.60D;
    private static final double BOAT_COUPLING = 0.08D;

    private ClientCurrents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        Player player = event.player;
        if (player != Minecraft.getInstance().player) return;
        CurrentStreaks.spawnAround(player);
        if (player.isSpectator()) return;

        if (player.isInWater() && !player.getAbilities().flying) {
            player.setSwimming(true);
            player.setPose(Pose.SWIMMING);
        } else if (player.getPose() == Pose.SWIMMING) {
            player.setSwimming(false);
            player.setPose(Pose.STANDING);
        }

        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            if (vehicle.getControllingPassenger() == player && vehicle.isInWater()) {
                Vec3 water = OceanCurrents.at(vehicle.level(), vehicle.getX(), vehicle.getY(), vehicle.getZ());
                Vec3 v = vehicle.getDeltaMovement();
                vehicle.setDeltaMovement(v.x + (water.x - v.x) * BOAT_COUPLING, v.y, v.z + (water.z - v.z) * BOAT_COUPLING);
            }
            return;
        }

        if (!player.isInWater() || player.getAbilities().flying) return;
        Vec3 water = OceanCurrents.at(player.level(), player.getX(), player.getY(), player.getZ());
        Vec3 v = player.getDeltaMovement();
        boolean activelySwimmingVertically =
                Minecraft.getInstance().options.keyJump.isDown() ||
                Minecraft.getInstance().options.keyShift.isDown();
        double neutralY = activelySwimmingVertically ? v.y : 0.0D;
        double currentSpeed = Math.hypot(water.x, water.z);
        double threshold = OceanographyConfig.fastCurrentThresholdBlocksPerTick();
        double range = CurrentField.MAX_SPEED - threshold;
        double strength = range > 0.0D
                ? Math.max(0.0D, Math.min(1.0D, (currentSpeed - threshold) / range))
                : 0.0D;
        double drag = WEAK_CURRENT_DRAG + strength * (STRONG_CURRENT_DRAG - WEAK_CURRENT_DRAG);

        player.setDeltaMovement(
                v.x + (water.x - v.x) * drag,
                neutralY,
                v.z + (water.z - v.z) * drag);
    }
}
