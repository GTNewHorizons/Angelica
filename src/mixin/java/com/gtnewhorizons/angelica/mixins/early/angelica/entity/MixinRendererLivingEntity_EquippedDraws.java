package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity_EquippedDraws {

    @Surround(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
    )
    private void angelica$countEquippedDraws() {
        BatchEligibility.beginExpectedDraws(GLSMDraws.drawCalls);
    }

    @Surround.Finally
    private void angelica$endEquippedDraws() {
        BatchEligibility.endExpectedDraws(GLSMDraws.drawCalls);
    }
}
