package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.DeferredEntityOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RendererLivingEntity.class, priority = 1200)
public class MixinRendererLivingEntity_DeferredEntityOverlay {

    /**
     * Intercepts the first renderPassModel.render() call in the shouldRenderPass loop.
     * When an entity overlay pass is active, captures the animation params and modelview
     * matrix for deferred rendering, skipping the immediate render.
     */
    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0)
    )
    private void angelica$maybeDeferOverlay(ModelBase model, Entity entity,
            float limbSwing, float limbSwingAmount, float ageInTicks,
            float headYaw, float headPitch, float scale,
            Operation<Void> original) {
        if (DeferredEntityOverlay.isOverlayPassActive()) {
            DeferredEntityOverlay.deferRender(limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
        } else {
            original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
        }
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
