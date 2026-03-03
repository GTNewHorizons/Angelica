package com.gtnewhorizons.angelica.mixins.early.angelica.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.ffp.TessellatorStreamingDrawer;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts Tessellator.draw() to use streaming VBO+VAO for core profile rendering,
 * except when GTNHLib needs to intercept (display list compilation, DirectTessellator capture).
 */
@Mixin(Tessellator.class)
public class MixinTessellator_CoreProfile {

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void angelica$coreProfileDraw(CallbackInfoReturnable<Integer> cir) {
        // Let GTNHLib handle display list compilation and DirectTessellator capture
        if (TessellatorManager.shouldInterceptDraw((Tessellator)(Object)this)) return;
        cir.setReturnValue(TessellatorStreamingDrawer.draw((Tessellator)(Object)this));
    }
}
