package com.redfoxsenpai.oceanography.client;

import com.redfoxsenpai.oceanography.entity.SchoolingFishEntity;
import net.minecraft.client.model.CodModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class HerringRenderer extends MobRenderer<SchoolingFishEntity, CodModel<SchoolingFishEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/fish/cod.png");

    public HerringRenderer(EntityRendererProvider.Context context) {
        super(context, new CodModel<>(context.bakeLayer(ModelLayers.COD)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(SchoolingFishEntity entity) {
        return TEXTURE;
    }
}