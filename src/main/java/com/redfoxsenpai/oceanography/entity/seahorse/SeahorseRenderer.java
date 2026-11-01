package com.redfoxsenpai.oceanography.entity.seahorse;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SeahorseRenderer extends GeoEntityRenderer<SeahorseEntity> {

    public SeahorseRenderer(EntityRendererProvider.Context context) {
        super(context, new SeahorseModel());
    }
}