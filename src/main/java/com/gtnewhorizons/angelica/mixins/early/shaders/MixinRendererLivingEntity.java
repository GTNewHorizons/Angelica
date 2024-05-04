package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.sugar.Local;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity {

    @Inject(
        method = "doRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;getColorMultiplier(Lnet/minecraft/entity/EntityLivingBase;FF)I", shift = At.Shift.AFTER),
        locals = LocalCapture.CAPTURE_FAILHARD,
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;getBrightness(F)F"),
            to = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OpenGlHelper;setActiveTexture(I)V", ordinal = 0)
        )
    )
    private void iris$setEntityColor(EntityLivingBase elb, double d1, double d2, double d3, float f1, float f2, CallbackInfo ci, @Local int j) {
        float a = (j >> 24 & 255) / 255.0F;
        float r = (j >> 16 & 255) / 255.0F;
        float g = (j >> 8 & 255) / 255.0F;
        float b = (j & 255) / 255.0F;
        CapturedRenderingState.INSTANCE.setCurrentEntityColor(r, g, b, a);
    }

}
