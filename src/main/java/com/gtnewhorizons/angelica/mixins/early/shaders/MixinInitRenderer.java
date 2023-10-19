package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.GLDebug;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpenGlHelper.class)
public class MixinInitRenderer {
    @Inject(method = "initializeTextures", at = @At("RETURN"))
    private static void angelica$initializeRenderer(CallbackInfo ci) {
        Iris.identifyCapabilities();
        GLDebug.initRenderer();
        IrisRenderSystem.initRenderer();
        Iris.onRenderSystemInit();
    }
}
