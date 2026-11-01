package com.redfoxsenpai.oceanography.client;

import com.redfoxsenpai.oceanography.entity.ModEntityTypes;
import com.redfoxsenpai.oceanography.entity.seahorse.SeahorseRenderer;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.redfoxsenpai.oceanography.Oceanography;

@Mod.EventBusSubscriber(modid = Oceanography.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ModEntityRenderers {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntityTypes.SEAHORSE.get(),
                SeahorseRenderer::new);
    }
}