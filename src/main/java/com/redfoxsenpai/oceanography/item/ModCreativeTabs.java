package com.redfoxsenpai.oceanography.item;

import com.redfoxsenpai.oceanography.Oceanography;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.core.registries.Registries;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Oceanography.MODID);

    public static final RegistryObject<CreativeModeTab> OCEANOGRAPHY_TAB = TABS.register("oceanography_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.oceanography"))
                    .icon(() -> new ItemStack(ModItems.CHUM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CHUM.get());
                        output.accept(ModItems.FISH_FOOD.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}