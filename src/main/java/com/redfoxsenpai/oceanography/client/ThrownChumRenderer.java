package com.redfoxsenpai.oceanography.client;

import com.redfoxsenpai.oceanography.entity.ThrownChumEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class ThrownChumRenderer extends ThrownItemRenderer<ThrownChumEntity> {
    public ThrownChumRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, true);
    }
}