package com.gtnewhorizons.angelica.mixins.early.renderer;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.Shaders;
import com.gtnewhorizons.angelica.client.ShadersRender;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Shadow
    public abstract void disableLightmap(double p_78483_1_);

    @Inject(
            method = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.BEFORE,
                    target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
                    remap = false))
    public void angelica$applyHandDepth(CallbackInfo ci) {
        Shaders.applyHandDepth();
    }

    @Inject(
            method = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V",
            at = @At(
                    value = "FIELD",
                    shift = At.Shift.BEFORE,
                    target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", ordinal = 1),

            cancellable = true)
    public void angelica$checkCompositeRendered(float p_78476_1_, int p_78476_2_, CallbackInfo ci) {
        if (!Shaders.isCompositeRendered) {
            ci.cancel();
        }
        this.disableLightmap(p_78476_1_);
    }

    @Redirect(
            method = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemInFirstPerson(F)V"))
    public void angelica$renderItemInFirstPerson(ItemRenderer itemRenderer, float partialTicks) {
        ShadersRender.renderItemFP(itemRenderer, partialTicks);
    }
}
