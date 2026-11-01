package com.redfoxsenpai.oceanography.entity.seahorse;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import com.redfoxsenpai.oceanography.Oceanography;

public class SeahorseModel extends GeoModel<SeahorseEntity> {

    @Override
    public ResourceLocation getModelResource(SeahorseEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(
                Oceanography.MODID,
                "geo/entity/seahorse.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SeahorseEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(
                Oceanography.MODID,
                "textures/entity/seahorse_1.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SeahorseEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(
                Oceanography.MODID,
                "animations/entity/seahorse.animation.json");
    }
}