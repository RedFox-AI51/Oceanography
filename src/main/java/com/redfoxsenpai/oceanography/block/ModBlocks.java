package com.redfoxsenpai.oceanography.block;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.sounds.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Oceanography.MODID);

    public static final RegistryObject<Block> FISH_FOOD = BLOCKS.register("fish_food", () ->
            new FishFoodBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(0.0F)
                    .sound(SoundType.LILY_PAD)));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}