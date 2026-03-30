package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.DeferredEntityOverlay;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_DeferredEntityOverlay {

    /**
     * Clear stale deferred entries before collecting new ones.
     */
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void angelica$clearStaleDeferredOverlays(CallbackInfo ci) {
        DeferredEntityOverlay.clear();
    }

    /**
     * Render deferred entity overlays after all entities have been drawn,
     * so additive-blended overlays composite correctly without depth issues.
     */
    @Inject(method = "renderEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V"))
    private void angelica$renderDeferredOverlays(CallbackInfo ci) {
        DeferredEntityOverlay.renderAll();
    }
}
