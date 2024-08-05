package com.gtnewhorizons.angelica.mixins.early.angelica.startup;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OpenGlHelper.class, priority = 100)
public class MixinInitGLStateManager {
    @Inject(method = "initializeTextures", at = @At("RETURN"))
    private static void angelica$initializeGLStateManager(CallbackInfo ci) {
        GLStateManager.preInit();
        GLStateManager.init();
    }
}
