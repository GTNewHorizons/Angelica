package com.gtnewhorizons.angelica.mixins.early.angelica.startup;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.client.rendering.TextureTracker;
import com.gtnewhorizons.angelica.client.rendering.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.compat.DriverCompatabilityCheck;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.render.SelectionBoxRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.launchwrapper.Launch;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OpenGlHelper.class, priority = 100)
public class MixinInitGLStateManager {

    @Inject(method = "initializeTextures", at = @At("RETURN"))
    private static void angelica$initializeGLStateManager(CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();
        GLStateManager.setDrawableGL(Display.getDrawable());
        GLStateManager.initialize(GLSMInitConfig.builder()
            .displaySize(mc.displayWidth, mc.displayHeight)
            .lwjglDebug(AngelicaMod.lwjglDebug)
            .streamingUploadStrategy(AngelicaMod.options().advanced.streamingUploadStrategy)
            .framebufferSupported(OpenGlHelper.framebufferSupported)
            .fboEnabled(mc.gameSettings.fboEnable)
            .directDrawer(TessellatorStreamingDrawer::drawDirect)
            .streamingDrawerDestroy(TessellatorStreamingDrawer::destroy)
            .postInitCallback(SelectionBoxRenderer::init)
            .build());

        if (Launch.blackboard != null && Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"))) {
            System.setProperty("angelica.dumpShaders", "true");
        }

        GLSMHooks.LIGHTMAP_COORDS.addListener(event -> {
            OpenGlHelper.lastBrightnessX = event.x;
            OpenGlHelper.lastBrightnessY = event.y;
        });

        GLSMHooks.TEXTURE_DELETE.addListener(event ->
            TextureTracker.INSTANCE.onDeleteTexture(event.textureId));

        DriverCompatabilityCheck.checkDriverCompatibility();
    }
}
