package com.redfoxsenpai.oceanography.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.redfoxsenpai.oceanography.entity.ChumEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class ChumRenderer extends EntityRenderer<ChumEntity> {

    public ChumRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChumEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // intentionally empty — particles in ChumEntity#tick() are the visual for now
    }

    @Override
    public ResourceLocation getTextureLocation(ChumEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png"); // unused placeholder
    }
}