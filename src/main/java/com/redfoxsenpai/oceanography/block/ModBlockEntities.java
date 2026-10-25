package com.redfoxsenpai.oceanography.block;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Oceanography.MODID);

    public static final RegistryObject<BlockEntityType<FishFoodBlockEntity>> FISH_FOOD =
            BLOCK_ENTITIES.register("fish_food", () ->
                    BlockEntityType.Builder.of(FishFoodBlockEntity::new, ModBlocks.FISH_FOOD.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}