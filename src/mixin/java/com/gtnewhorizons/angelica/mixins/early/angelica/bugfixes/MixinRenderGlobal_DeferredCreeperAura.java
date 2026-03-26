package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.DeferredCreeperAura;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_DeferredCreeperAura {

    /**
     * Clear stale deferred entries before collecting new ones.
     */
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void angelica$clearStaleDeferredAuras(CallbackInfo ci) {
        DeferredCreeperAura.clear();
    }

    /**
     * Render deferred charged creeper auras after all entities have been drawn,
     * so the additive-blended aura renders without running into depth issues.
     */
    @Inject(method = "renderEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V"))
    private void angelica$renderDeferredCreeperAuras(CallbackInfo ci) {
        DeferredCreeperAura.renderAll();
    }
}
