package com.gtnewhorizons.angelica.mixins.early.angelica.startup;

import com.gtnewhorizon.gtnhlib.core.GTNHLibCore;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.client.rendering.TextureTracker;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.compat.DriverCompatabilityCheck;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.render.SelectionBoxRenderer;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import org.embeddedt.embeddium.impl.gl.debug.GLDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OpenGlHelper.class, priority = 100)
public class MixinInitGLStateManager {

    @Inject(method = "initializeTextures", at = @At("HEAD"))
    private static void angelica$installSDLDrawable(CallbackInfo ci) {
        SDLGPUGate.ensureDrawableInstalled();
    }

    @Inject(method = "initializeTextures", at = @At("RETURN"))
    private static void angelica$initializeGLStateManager(CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.fboEnable = true; // Angelica & many other GTNH features require FBO's
        GLStateManager.setDrawableGL(Display.getDrawable());
        if (AngelicaMod.lwjglDebug) {
            System.setProperty(SystemProperties.KEY_CELERITAS_ENABLE_GL_DEBUG, "true");
            GLDebug.reloadDebugState();
        }
        GLStateManager.initialize(GLSMInitConfig.builder()
            .displaySize(mc.displayWidth, mc.displayHeight)
            .lwjglDebug(AngelicaMod.lwjglDebug)
            .streamingUploadStrategy(ClientProxy.options().advanced.streamingUploadStrategy)
            .noErrorChecks(AngelicaConfig.disableErrorChecks && GTNHLibCore.isObf() && !AngelicaMod.lwjglDebug)
            .enableDSA(AngelicaConfig.enableDSA)
            .directDrawer(TessellatorStreamingDrawer::drawDirect)
            .streamingDrawerDestroy(TessellatorStreamingDrawer::destroy)
            .postInitCallback(SelectionBoxRenderer::init)
            .build());

        GLSMHooks.LIGHTMAP_COORDS.addListener(event -> {
            OpenGlHelper.lastBrightnessX = event.x;
            OpenGlHelper.lastBrightnessY = event.y;
        });

        GLSMHooks.TEXTURE_DELETE.addListener(event ->
            TextureTracker.INSTANCE.onDeleteTexture(event.textureId));

        DriverCompatabilityCheck.checkDriverCompatibility();
    }
}
