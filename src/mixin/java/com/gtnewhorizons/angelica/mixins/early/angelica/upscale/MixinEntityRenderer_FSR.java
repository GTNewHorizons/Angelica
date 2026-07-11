package com.gtnewhorizons.angelica.mixins.early.angelica.upscale;

import com.gtnewhorizons.angelica.upscale.FSR1;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps the world pass so it renders at a reduced internal resolution and is upscaled with
 * FSR 1.0 before the GUI draws at native resolution.
 */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_FSR {

    @WrapOperation(
        method = "updateCameraAndRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V"))
    private void angelica$fsrWrapRenderWorld(EntityRenderer instance, float partialTicks, long nanoTime,
        Operation<Void> original) {
        final Minecraft mc = Minecraft.getMinecraft();
        FSR1.beginWorldRender(mc);
        try {
            original.call(instance, partialTicks, nanoTime);
        } finally {
            FSR1.endWorldRender(mc);
        }
    }
}
