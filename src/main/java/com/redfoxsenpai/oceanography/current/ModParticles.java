package com.redfoxsenpai.oceanography.current;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Oceanography.MODID);

    /** A glowing mote that rides the ocean current and leaves a fading trail, so the flow shows up as streaks. */
    public static final RegistryObject<SimpleParticleType> CURRENT_MOTE =
            PARTICLES.register("current_mote", () -> new SimpleParticleType(false));

    private ModParticles() {}

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
