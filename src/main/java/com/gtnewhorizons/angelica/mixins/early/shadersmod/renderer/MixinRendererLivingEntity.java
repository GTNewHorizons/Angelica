package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity {
    // TODO: Rendering
    @Shadow
    protected abstract int getColorMultiplier(EntityLivingBase p_77030_1_, float p_77030_2_, float p_77030_3_);

    @Inject(at = @At("HEAD"), method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V")
    private void angelica$setEntityHurtFlash(EntityLivingBase p_76986_1_, double p_76986_2_, double p_76986_4_, double p_76986_6_, float p_76986_8_, float p_76986_9_, CallbackInfo ci) {
        if (!Shaders.useEntityHurtFlash) {
            Shaders.setEntityHurtFlash(
                    p_76986_1_.hurtTime <= 0 && p_76986_1_.deathTime <= 0 ? 0 : 102,
                    this.getColorMultiplier(p_76986_1_, p_76986_1_.getBrightness(p_76986_9_), p_76986_9_));
        }
    }

    @Inject(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V",
                    value = "INVOKE"),
            method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V")
    private void angelica$resetEntityHurtFlash(CallbackInfo ci) {
        Shaders.resetEntityHurtFlash();
    }

    @Inject(
            at = @At(
                    ordinal = 1,
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/OpenGlHelper;setActiveTexture(I)V",
                    value = "INVOKE"),
            method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V")
    private void angelica$disableLightmap(CallbackInfo ci) {
        Shaders.disableLightmap();
    }

    @Inject(
            at = @At(
                    ordinal = 2,
                    remap = false,
                    shift = At.Shift.AFTER,
                    target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V",
                    value = "INVOKE"),
            method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V")
    private void angelica$beginLivingDamage(CallbackInfo ci) {
        Shaders.beginLivingDamage();
    }

    @Inject(
            at = @At(
                    ordinal = 3,
                    remap = false,
                    shift = At.Shift.AFTER,
                    target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V",
                    value = "INVOKE"),
            method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V")
    private void angelica$endLivingDamage(CallbackInfo ci) {
        Shaders.endLivingDamage();
    }

    @Inject(
            at = @At(
                    ordinal = 3,
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/OpenGlHelper;setActiveTexture(I)V",
                    value = "INVOKE"),
            method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V")
    private void angelica$enableLightmap(CallbackInfo ci) {
        Shaders.enableLightmap();
    }

}
