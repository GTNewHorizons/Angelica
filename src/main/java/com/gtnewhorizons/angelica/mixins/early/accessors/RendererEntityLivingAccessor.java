package com.gtnewhorizons.angelica.mixins.early.accessors;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RendererLivingEntity.class)
public interface RendererEntityLivingAccessor {

    @Accessor
    ModelBase getMainModel();

    @Accessor
    ModelBase getRenderPassModel();
}
