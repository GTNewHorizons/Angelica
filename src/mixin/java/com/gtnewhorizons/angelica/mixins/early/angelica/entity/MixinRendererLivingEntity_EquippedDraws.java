package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.OperationArgs;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity_EquippedDraws {

    @Unique private static final Object[] angelica$equippedArgs = new Object[3];

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V"
        )
    )
    private void angelica$countEquippedDraws(RendererLivingEntity self, EntityLivingBase entity, float partialTicks, Operation<Void> original) {
        final Object[] args = angelica$equippedArgs;
        args[0] = self;
        args[1] = entity;
        args[2] = OperationArgs.boxed(args[2], partialTicks);
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
