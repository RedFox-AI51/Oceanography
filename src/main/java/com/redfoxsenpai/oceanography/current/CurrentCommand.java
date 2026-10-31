package com.redfoxsenpai.oceanography.current;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /oceancurrent  - tells you which way the water is moving where you are standing, and how fast. Handy for testing:
 * stand in the ocean, run it, then drop an item or float a boat and watch it follow.
 */
@Mod.EventBusSubscriber(modid = Oceanography.MODID)
public final class CurrentCommand {

    private static final String[] COMPASS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    private CurrentCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("oceancurrent").executes(context -> {
            CommandSourceStack source = context.getSource();
            Vec3 pos = source.getPosition();
            Vec3 water = OceanCurrents.at(source.getLevel(), pos.x, pos.y, pos.z);

            double perSecond = Math.hypot(water.x, water.z) * 20.0D;
            String message;
            if (perSecond < 0.005D) {
                message = "No ocean current here (not in an ocean, or the water is still at this depth).";
            } else {
                // Minecraft: +x is east, +z is south, so north is -z
                double bearing = Math.toDegrees(Math.atan2(water.x, -water.z));
                if (bearing < 0.0D) bearing += 360.0D;
                String direction = COMPASS[(int) Math.round(bearing / 45.0D) % 8];
                double depth = Math.max(0.0D, source.getLevel().getSeaLevel() - pos.y);
                message = String.format("Current here flows %s at %.2f blocks/second (%.0f blocks below the surface; %s).",
                        direction, perSecond, depth, CurrentField.depthFactor(depth) < 0.0D ? "deep return flow" : "surface layer");
            }
            final String text = message;
            source.sendSuccess(() -> Component.literal(text), false);
            return 1;
        }));
    }
}
