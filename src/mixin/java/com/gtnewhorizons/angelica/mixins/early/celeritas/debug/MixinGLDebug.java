package com.gtnewhorizons.angelica.mixins.early.celeritas.debug;

import org.embeddedt.embeddium.impl.gl.debug.GLDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GLDebug.class, remap = false)
public class MixinGLDebug {

    @Inject(method = "nameObject", at = @At("HEAD"))
    private static void angelica$bridgeToBackendLabels(int id, int object, String name, CallbackInfo ci) {
        // Stupid name collisions
        com.gtnewhorizons.angelica.glsm.GLDebug.nameObject(id, object, name);
    }
}
