package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity_EquippedDraws {

    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
    )
    private void angelica$beginEquippedDraws(CallbackInfo ci) {
        BatchEligibility.beginExpectedDraws(GLStateManager.drawCalls);
    }

    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V", shift = At.Shift.AFTER)
    )
    private void angelica$endEquippedDraws(CallbackInfo ci) {
        BatchEligibility.endExpectedDraws(GLStateManager.drawCalls);
    }
}
