package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity_ModelPassDraws {

    @Surround(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V")
    )
    private void angelica$countModelPassDraws() {
        BatchEligibility.beginExpectedDraws(GLStateManager.drawCalls);
    }

    @Surround.Finally
    private void angelica$endModelPassDraws() {
        BatchEligibility.endExpectedDraws(GLStateManager.drawCalls);
    }
}
