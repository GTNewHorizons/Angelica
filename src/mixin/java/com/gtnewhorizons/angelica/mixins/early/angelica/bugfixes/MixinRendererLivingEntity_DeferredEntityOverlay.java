package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.DeferredEntityOverlay;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RendererLivingEntity.class, priority = 1200)
public class MixinRendererLivingEntity_DeferredEntityOverlay {

    @WrapWithCondition(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0)
    )
    private boolean angelica$maybeDeferOverlay(ModelBase model, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw, float headPitch, float scale) {
        if (!DeferredEntityOverlay.isOverlayPassActive()) return true;
        DeferredEntityOverlay.deferRender(limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
        return false;
    }

    /**
     * Clear a stale overlayPassActive flag after the shouldRenderPass loop.
     * Handles the case where markOverlayPass fired but shouldRenderPass returned <= 0,
     * so renderPassModel.render() was never called and deferRender never consumed the flag.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
    )
    private void angelica$clearStaleOverlayFlag(CallbackInfo ci) {
        DeferredEntityOverlay.clearStaleOverlayFlag();
    }
}
