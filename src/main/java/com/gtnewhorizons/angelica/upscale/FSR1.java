package com.gtnewhorizons.angelica.upscale;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IMinecraftMainFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * AMD FidelityFX Super Resolution 1.0 for the world pass.
 *
 * For the duration of renderWorld, the main framebuffer is swapped for a scaled one and the
 * apparent display size is shrunk to match. Everything that sizes itself off the main framebuffer
 * or the display size — the vanilla pipeline, the Iris render targets, shaderpack viewWidth/Height
 * uniforms — therefore renders coherently at the reduced resolution. Afterwards the scaled result
 * is upscaled back into the real main framebuffer in two passes (EASU then RCAS) and the GUI
 * renders on top at native resolution.
 */
public final class FSR1 {

    private static boolean triedInit = false;
    private static boolean available = false;

    private static Framebuffer scaledFb = null;
    private static Framebuffer savedMainFb = null;

    private static int midTex = 0, midFbo = 0;
    private static int easuProgram = 0, rcasProgram = 0;
    private static int uEasuCon0 = -1, uEasuInputMax = -1;
    private static int uRcasSharpness = -1;

    private static int midW = -1, midH = -1;

    private static int nativeW, nativeH, scaledW, scaledH;
    private static boolean renderingScaled = false;

    private FSR1() {}

    public static boolean isEnabled() {
        return AngelicaConfig.fsrRenderScale < 100;
    }

    private static boolean shouldRun(Minecraft mc) {
        return isEnabled() && mc.theWorld != null
            && OpenGlHelper.isFramebufferEnabled()
            && GLContext.getCapabilities().OpenGL33;
    }

    /** Swaps in the scaled framebuffer and shrinks the apparent display size for the world pass. */
    public static void beginWorldRender(Minecraft mc) {
        if (renderingScaled || !shouldRun(mc)) {
            return;
        }
        nativeW = mc.displayWidth;
        nativeH = mc.displayHeight;
        scaledW = Math.max(1, (nativeW * AngelicaConfig.fsrRenderScale + 99) / 100);
        scaledH = Math.max(1, (nativeH * AngelicaConfig.fsrRenderScale + 99) / 100);
        if (scaledW >= nativeW || scaledH >= nativeH) {
            return;
        }
        try {
            if (scaledFb == null || scaledFb.framebufferWidth != scaledW || scaledFb.framebufferHeight != scaledH) {
                if (scaledFb != null) {
                    scaledFb.deleteFramebuffer();
                }
                scaledFb = new Framebuffer(scaledW, scaledH, true);
            }
        } catch (Exception e) {
            com.gtnewhorizons.angelica.AngelicaMod.LOGGER
                .error("FSR1: could not create scaled framebuffer — disabling for this session", e);
            AngelicaConfig.fsrRenderScale = 100;
            return;
        }

        final IMinecraftMainFramebuffer accessor = (IMinecraftMainFramebuffer) mc;
        savedMainFb = accessor.angelica$getMainFramebuffer();
        accessor.angelica$setMainFramebuffer(scaledFb);
        mc.displayWidth = scaledW;
        mc.displayHeight = scaledH;
        scaledFb.bindFramebuffer(true);
        renderingScaled = true;
    }

    /** Restores the real framebuffer/display size and upscales the world into it. */
    public static void endWorldRender(Minecraft mc) {
        if (!renderingScaled) {
            return;
        }
        renderingScaled = false;
        ((IMinecraftMainFramebuffer) mc).angelica$setMainFramebuffer(savedMainFb);
        mc.displayWidth = nativeW;
        mc.displayHeight = nativeH;
        try {
            upscale(mc);
        } catch (Exception e) {
            com.gtnewhorizons.angelica.AngelicaMod.LOGGER
                .error("FSR1 upscale failed — disabling render scale for this session", e);
            AngelicaConfig.fsrRenderScale = 100;
            savedMainFb.bindFramebuffer(true);
        }
        savedMainFb = null;
    }

    private static void upscale(Minecraft mc) {
        if (!ensureResources()) {
            AngelicaConfig.fsrRenderScale = 100;
            savedMainFb.bindFramebuffer(true);
            return;
        }

        // Fullscreen-pass state; fixed-function fragment state must not clip or blend our quads.
        final boolean hadDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean hadBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean hadAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        final boolean hadScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GLStateManager.disableDepthTest();
        GLStateManager.disableBlend();
        GLStateManager.disableAlphaTest();
        if (hadScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glDepthMask(false);

        // 1. EASU: scaled world -> native-size intermediate.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, midFbo);
        GL11.glViewport(0, 0, nativeW, nativeH);
        GL20.glUseProgram(easuProgram);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, scaledFb.framebufferTexture);
        GL20.glUniform4f(uEasuCon0,
            (float) scaledW / nativeW,
            (float) scaledH / nativeH,
            0.5f * scaledW / nativeW - 0.5f,
            0.5f * scaledH / nativeH - 0.5f);
        GL20.glUniform2i(uEasuInputMax, scaledW - 1, scaledH - 1);
        drawFullscreenQuad();

        // 2. RCAS: intermediate -> the real main framebuffer at native size.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedMainFb.framebufferObject);
        GL11.glViewport(0, 0, nativeW, nativeH);
        GL20.glUseProgram(rcasProgram);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, midTex);
        // Slider 0..100 -> 2..0 stops of sharpness reduction (100 = sharpest).
        final float stops = (100 - AngelicaConfig.fsrSharpness) / 50.0f;
        GL20.glUniform1f(uRcasSharpness, (float) Math.pow(2.0, -stops));
        drawFullscreenQuad();

        // 3. Cleanup: leave the real framebuffer bound with a cleared depth buffer for the GUI
        // (the world's depth lives in the scaled framebuffer and is meaningless at native res).
        GL20.glUseProgram(0);
        GLStateManager.glDepthMask(true);
        savedMainFb.bindFramebuffer(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        if (hadDepth) GLStateManager.enableDepthTest();
        if (hadBlend) GLStateManager.enableBlend();
        if (hadAlpha) GLStateManager.enableAlphaTest();
        if (hadScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    private static void drawFullscreenQuad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f(1.0f, -1.0f);
        GL11.glVertex2f(1.0f, 1.0f);
        GL11.glVertex2f(-1.0f, 1.0f);
        GL11.glEnd();
    }

    private static boolean ensureResources() {
        if (!triedInit) {
            triedInit = true;
            available = initPrograms();
        }
        if (!available) {
            return false;
        }
        if (midW != nativeW || midH != nativeH) {
            midTex = recreateTarget(midTex, nativeW, nativeH);
            midFbo = recreateFbo(midFbo, midTex);
            midW = nativeW;
            midH = nativeH;
        }
        return true;
    }

    private static int recreateTarget(int oldTex, int w, int h) {
        if (oldTex != 0) {
            GL11.glDeleteTextures(oldTex);
        }
        final int tex = GL11.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return tex;
    }

    private static int recreateFbo(int oldFbo, int tex) {
        if (oldFbo != 0) {
            GL30.glDeleteFramebuffers(oldFbo);
        }
        final int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, tex, 0);
        return fbo;
    }

    private static boolean initPrograms() {
        try {
            final int vert = compile(GL20.GL_VERTEX_SHADER, load("fullscreen.vsh"));
            easuProgram = link(vert, compile(GL20.GL_FRAGMENT_SHADER, load("easu.fsh")));
            rcasProgram = link(vert, compile(GL20.GL_FRAGMENT_SHADER, load("rcas.fsh")));
            GL20.glUseProgram(easuProgram);
            GL20.glUniform1i(GL20.glGetUniformLocation(easuProgram, "uSource"), 0);
            uEasuCon0 = GL20.glGetUniformLocation(easuProgram, "uCon0");
            uEasuInputMax = GL20.glGetUniformLocation(easuProgram, "uInputMax");
            GL20.glUseProgram(rcasProgram);
            GL20.glUniform1i(GL20.glGetUniformLocation(rcasProgram, "uSource"), 0);
            uRcasSharpness = GL20.glGetUniformLocation(rcasProgram, "uSharpness");
            GL20.glUseProgram(0);
            return true;
        } catch (Exception e) {
            com.gtnewhorizons.angelica.AngelicaMod.LOGGER.error("FSR1 shader init failed", e);
            return false;
        }
    }

    private static String load(String name) throws Exception {
        try (InputStream in = FSR1.class.getResourceAsStream("/assets/angelica/shaders/fsr1/" + name)) {
            if (in == null) throw new IllegalStateException("Missing FSR1 shader " + name);
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        }
    }

    private static int compile(int type, String src) {
        final int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("FSR1 shader compile failed: " + GL20.glGetShaderInfoLog(shader, 4096));
        }
        return shader;
    }

    private static int link(int vert, int frag) {
        final int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vert);
        GL20.glAttachShader(program, frag);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("FSR1 program link failed: " + GL20.glGetProgramInfoLog(program, 4096));
        }
        return program;
    }
}
