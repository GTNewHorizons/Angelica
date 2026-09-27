package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderLoader.class, remap = false)
public class MixinShaderLoader {

    /** The desktop GL 3.2 capability check can fail on ES 3.2; ES must never take the GLSL 1.20 path. */
    @Inject(method = "useLegacyGlsl", at = @At("HEAD"), cancellable = true)
    private static void angelica$noLegacyGlslOnGles(CallbackInfoReturnable<Boolean> cir) {
        if (RenderSystem.isGLES()) {
            cir.setReturnValue(false);
        }
    }
}
