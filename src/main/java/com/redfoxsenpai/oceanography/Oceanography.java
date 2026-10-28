package com.redfoxsenpai.oceanography;

import com.redfoxsenpai.oceanography.entity.DepthKeeping;
import com.redfoxsenpai.oceanography.entity.ModEntityTypes;
import com.redfoxsenpai.oceanography.entity.SchoolingFishEntity;
import com.redfoxsenpai.oceanography.item.ModItems;
import com.redfoxsenpai.oceanography.item.ModCreativeTabs;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.FluidTags;

import com.redfoxsenpai.oceanography.block.ModBlocks;
import com.redfoxsenpai.oceanography.block.ModBlockEntities;

@Mod(Oceanography.MODID)
public class Oceanography {

    public static final String MODID = "oceanography";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Oceanography() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntityTypes.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);

        modEventBus.addListener(this::onEntityAttributeCreation);
        modEventBus.addListener(this::onSpawnPlacementRegister);

        LOGGER.info("Oceanography mod initializing");
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.HERRING.get(), SchoolingFishEntity.createAttributes().build());
    }

    private void onSpawnPlacementRegister(SpawnPlacementRegisterEvent event) {
        event.register(ModEntityTypes.HERRING.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) ->
                        pos.getY() <= level.getSeaLevel() - 2 &&
                        pos.getY() >= level.getSeaLevel() - DepthKeeping.SPAWN_DEPTH &&
                        level.getFluidState(pos).is(FluidTags.WATER) &&
                        level.getFluidState(pos.above()).is(FluidTags.WATER),
                SpawnPlacementRegisterEvent.Operation.AND);
    }
}