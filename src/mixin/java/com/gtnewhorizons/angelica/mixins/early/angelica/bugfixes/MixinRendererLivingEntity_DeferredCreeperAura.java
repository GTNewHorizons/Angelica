package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.DeferredCreeperAura;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RendererLivingEntity.class, priority = 1200)
public class MixinRendererLivingEntity_DeferredCreeperAura {

    /**
     * Intercepts the first renderPassModel.render() call in the shouldRenderPass loop.
     * When a charged creeper aura pass is active, we capture the animation params and modelview
     * matrix for deferred rendering, skipping the immediate render.
     */
    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0)
    )
    private void angelica$maybeDeferCreeperAura(ModelBase model, Entity entity,
            float limbSwing, float limbSwingAmount, float ageInTicks,
            float headYaw, float headPitch, float scale,
            Operation<Void> original) {
        if (DeferredCreeperAura.isAuraPassActive()) {
            DeferredCreeperAura.deferRender(limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
        } else {
            original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
        }
    }
}
