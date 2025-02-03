package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
import net.coderbot.iris.pipeline.HandRenderer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xonin.backhand.client.hooks.ItemRendererHooks;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void iris$skipTranslucentHands(float partialTicks, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()) {
            boolean isHandTranslucent = HandRenderer.INSTANCE.isHandTranslucent(InteractionHand.MAIN_HAND);
            if (HandRenderer.INSTANCE.isRenderingSolid() && isHandTranslucent) {
                ci.cancel();
                if (ModStatus.isBackhandLoaded){
                    iris$skipTranslucentHandsBackhand(partialTicks, ci);
                    ItemRendererHooks.renderOffhandReturn(partialTicks);
                }
            } else if (!HandRenderer.INSTANCE.isRenderingSolid() && !isHandTranslucent) {
                ci.cancel();
                if (ModStatus.isBackhandLoaded){
                    iris$skipTranslucentHandsBackhand(partialTicks, ci);
                    ItemRendererHooks.renderOffhandReturn(partialTicks);
                }
            }
        }
    }

    // change injection to before Backhand's inject
    @Inject(method = "renderItemInFirstPerson", at = @At("RETURN"), cancellable = true, order = 900)
    private void iris$skipTranslucentHandsBackhand(float partialTicks, CallbackInfo ci) {
        if (ModStatus.isBackhandLoaded && IrisApi.getInstance().isShaderPackInUse()) {
            boolean isHandTranslucent = HandRenderer.INSTANCE.isHandTranslucent(InteractionHand.OFF_HAND);
            if (HandRenderer.INSTANCE.isRenderingSolid() && isHandTranslucent) {
                ci.cancel();
            } else if (!HandRenderer.INSTANCE.isRenderingSolid() && !isHandTranslucent) {
                ci.cancel();
            }
        }
    }
}
