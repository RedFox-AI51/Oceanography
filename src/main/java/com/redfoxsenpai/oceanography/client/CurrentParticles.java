package com.redfoxsenpai.oceanography.client;

import com.redfoxsenpai.oceanography.Oceanography;
import com.redfoxsenpai.oceanography.current.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Oceanography.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CurrentParticles {

    private CurrentParticles() {}

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CURRENT_MOTE.get(), CurrentMoteParticle.Provider::new);
    }
}
