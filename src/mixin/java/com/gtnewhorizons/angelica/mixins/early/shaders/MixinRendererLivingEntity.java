package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity {

    @WrapOperation(
        method="doRender",
        at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/RendererLivingEntity;getColorMultiplier(Lnet/minecraft/entity/EntityLivingBase;FF)I")
    )
    private int iris$setEntityColor(RendererLivingEntity instance, EntityLivingBase elb, float f0, float f1, Operation<Integer> original) {
        final int j = original.call(instance, elb, f0, f1);
        final float a = (j >> 24 & 255) / 255.0F;
        final float r = (j >> 16 & 255) / 255.0F;
        final float g = (j >> 8 & 255) / 255.0F;
        final float b = (j & 255) / 255.0F;
        CapturedRenderingState.INSTANCE.setCurrentEntityColor(r, g, b, a);
        return j;
    }

    @Inject(
        method="Lnet/minecraft/client/renderer/entity/RendererLivingEntity;doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at=@At(value="INVOKE",
        target="Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V", shift=At.Shift.BEFORE)
    )
    private void iris$teardownSpecialRenderConditions(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
