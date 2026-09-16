package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.rendering.GlintClock;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity_GlintClock {

    @Group(name = "angelica$globalGlintPhase", min = 1, max = 1)
    @Redirect(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 1, remap = false)
    )
    private void angelica$globalGlintPhase(float x, float y, float z) {
        GlintClock.translateArmorGlint();
    }

    @Group(name = "angelica$globalGlintPhase", min = 1, max = 1)
    @Redirect(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/glsm/GLStateManager;glTranslatef(FFF)V",
            ordinal = 1,
            remap = false
        )
    )
    private void angelica$globalGlintPhaseGlsm(float x, float y, float z) {
        GlintClock.translateArmorGlint();
    }
}
