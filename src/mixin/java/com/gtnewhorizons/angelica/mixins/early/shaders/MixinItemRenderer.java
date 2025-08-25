package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.mojang.InteractionHand;
import net.coderbot.iris.pipeline.HandRenderer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void iris$skipTranslucentHands(float partialTicks, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()) {
            boolean isHandTranslucent = HandRenderer.INSTANCE.isHandTranslucent(InteractionHand.MAIN_HAND);
            if (HandRenderer.INSTANCE.isRenderingSolid() && isHandTranslucent) {
                ci.cancel();
            } else if (!HandRenderer.INSTANCE.isRenderingSolid() && !isHandTranslucent) {
                ci.cancel();
            }
        }
    }
}
