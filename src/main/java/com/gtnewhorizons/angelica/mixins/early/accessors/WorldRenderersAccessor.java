package com.gtnewhorizons.angelica.mixins.early.accessors;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderGlobal.class)
public interface WorldRenderersAccessor {

    @Accessor
    WorldRenderer[] getWorldRenderers();
}
