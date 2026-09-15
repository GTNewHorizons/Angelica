package com.gtnewhorizons.umbra.mixins.early.startup;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OpenGlHelper.class, priority = 100)
public class MixinInitGLStateManager {

    @Inject(method = "initializeTextures", at = @At("HEAD"))
    private static void umbra$installSDLDrawable(CallbackInfo ci) {
        SDLGPUGate.ensureDrawableInstalled();
    }

    @Inject(method = "initializeTextures", at = @At("RETURN"))
    private static void umbra$initializeGLStateManager(CallbackInfo ci) {
        GLStateManager.setDrawableGL(Display.getDrawable());
        GLStateManager.initialize(GLSMInitConfig.builder().build());

        GLSMHooks.LIGHTMAP_COORDS.addListener(event -> {
            OpenGlHelper.lastBrightnessX = event.x;
            OpenGlHelper.lastBrightnessY = event.y;
        });
    }
}
