package com.redfoxsenpai.oceanography.entity;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Oceanography.MODID);

    /** Small schooling fish. Same hitbox as vanilla cod; size is randomised via Attributes.SCALE. */
    public static final RegistryObject<EntityType<SchoolingFishEntity>> HERRING =
            ENTITY_TYPES.register("herring", () ->
                    EntityType.Builder.<SchoolingFishEntity>of(SchoolingFishEntity::new, MobCategory.WATER_AMBIENT)
                            .sized(0.5F, 0.3F)
                            .eyeHeight(0.195F)
                            .clientTrackingRange(4)
                            .build("herring"));

    /** The chum slick sitting on the water surface. Invisible; particles are the visual. */
    public static final RegistryObject<EntityType<ChumEntity>> CHUM =
            ENTITY_TYPES.register("chum", () ->
                    EntityType.Builder.<ChumEntity>of(ChumEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(8)
                            .updateInterval(10)
                            .build("chum"));

    /** The chum item while it is in flight, before it hits the water. */
    public static final RegistryObject<EntityType<ThrownChumEntity>> THROWN_CHUM =
            ENTITY_TYPES.register("thrown_chum", () ->
                    EntityType.Builder.<ThrownChumEntity>of(ThrownChumEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("thrown_chum"));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}