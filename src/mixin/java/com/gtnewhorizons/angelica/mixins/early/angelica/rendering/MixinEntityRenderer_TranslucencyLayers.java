package com.gtnewhorizons.angelica.mixins.early.angelica.rendering;

import com.gtnewhorizons.angelica.rendering.translucency.TranslucencyLayers;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_TranslucencyLayers {

    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
    private void angelica$beginTranslucencyLayers(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        TranslucencyLayers.INSTANCE.beginFrame();
    }

    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", remap = false))
    private void angelica$compositeTranslucencyLayers(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        TranslucencyLayers.INSTANCE.composite();
    }
}
