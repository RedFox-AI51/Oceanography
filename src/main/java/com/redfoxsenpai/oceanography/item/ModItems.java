package com.redfoxsenpai.oceanography.item;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Oceanography.MODID);

    public static final RegistryObject<Item> CHUM =
            ITEMS.register("chum", () -> new ChumItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> FISH_FOOD =
            ITEMS.register("fish_food", () -> new FishFoodItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

}