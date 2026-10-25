package com.redfoxsenpai.oceanography.client;

import com.redfoxsenpai.oceanography.Oceanography;
import com.redfoxsenpai.oceanography.entity.ModEntityTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Oceanography.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.HERRING.get(), HerringRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.CHUM.get(), ChumRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.THROWN_CHUM.get(), ThrownChumRenderer::new);
    }
}