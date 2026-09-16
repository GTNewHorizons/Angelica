package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.OperationArgs;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity_ModelPassDraws {

    @Unique private static final Object[] angelica$modelPassArgs = new Object[8];

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V")
    )
    private void angelica$countModelPassDraws(ModelBase model, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw, float headPitch, float scale, Operation<Void> original) {
        final Object[] args = angelica$modelPassArgs;
        args[0] = model;
        args[1] = entity;
        args[2] = OperationArgs.boxed(args[2], limbSwing);
        args[3] = OperationArgs.boxed(args[3], limbSwingAmount);
        args[4] = OperationArgs.boxed(args[4], ageInTicks);
        args[5] = OperationArgs.boxed(args[5], headYaw);
        args[6] = OperationArgs.boxed(args[6], headPitch);
        args[7] = OperationArgs.boxed(args[7], scale);
        BatchEligibility.beginExpectedDraws(GLStateManager.drawCalls);
        try {
            original.call(args);
        } finally {
            args[0] = null;
            args[1] = null;
            BatchEligibility.endExpectedDraws(GLStateManager.drawCalls);
        }
    }
}
