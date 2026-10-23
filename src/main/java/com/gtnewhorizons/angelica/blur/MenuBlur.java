package com.gtnewhorizons.angelica.blur;

import com.gtnewhorizons.angelica.client.rendering.GlUniformFloat2v;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.render.PanoramaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.apache.commons.lang3.ArrayUtils;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Blurs the world behind open GUIs and fades in a configurable background gradient.
 * Based on Blur by tterrag (https://github.com/tterrag1098/Blur) under the MIT license, see LICENSE-Blur.md.
 * Reimplemented on top of Angelica's own blur shader instead of a vanilla post-processing shader.
 */
public class MenuBlur {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static GlProgram<BlurUniforms> blurProgram;
    private static StateSet allState;
    private static int emptyVao;
    // The blur runs on half resolution copies of the screen, which is much cheaper and looks the same once blurred
    private static int horizontalFbo, horizontalTexture, verticalFbo, verticalTexture;
    private static int sourceWidth, sourceHeight, halfWidth, halfHeight;

    private static boolean blurring;
    private static long fadeStartMillis;

    /** Called right after the world has been drawn, before the HUD and the open GUI. */
    public static void renderBlur() {
        final boolean shouldBlur = shouldBlur();
        if (shouldBlur && !blurring) {
            fadeStartMillis = System.currentTimeMillis();
        }
        blurring = shouldBlur;
        if (!blurring) return;

        // The radius grows with the fade, so the blur gets stronger over the first moments
        final float radius = (float) Math.floor(AngelicaConfig.menuBlurRadius * getProgress());
        if (radius < 1.0F) return;

        final Framebuffer mainFramebuffer = mc.getFramebuffer();
        if (blurProgram == null) init();

        final int previousProgram = GLStateManager.getActiveProgram();
        final int previousVao = GLStateManager.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int previousFilter = mainFramebuffer.framebufferFilter;
        final int stateDepth = GLStateManager.pushState(allState);
        try {
            ensureBlurTargets(mainFramebuffer.framebufferWidth, mainFramebuffer.framebufferHeight);

            GLStateManager.disableBlend();
            GLStateManager.disableDepthTest();
            GLStateManager.glDepthMask(false);
            GLStateManager.glColorMask(true, true, true, true);
            GLStateManager.disableCull();
            GLStateManager.disableScissorTest();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

            blurProgram.bind();
            GLStateManager.glBindVertexArray(emptyVao);
            final BlurUniforms uniforms = blurProgram.getInterface();
            // Each half resolution pixel covers two screen pixels, so half the radius gives the same strength
            uniforms.radius.setFloat(radius * 0.5F);

            // Horizontal pass, shrinking the screen to half size. Linear filtering makes every
            // half size pixel average the four screen pixels it covers instead of picking one.
            mainFramebuffer.setFramebufferFilter(GL11.GL_LINEAR);
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, horizontalFbo);
            GLStateManager.glViewport(0, 0, halfWidth, halfHeight);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, mainFramebuffer.framebufferTexture);
            uniforms.direction.set(1.0F / halfWidth, 0.0F);
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // Vertical pass, still at half size
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, verticalFbo);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, horizontalTexture);
            uniforms.direction.set(0.0F, 1.0F / halfHeight);
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // Stretch the result back over the screen. A radius of 0 makes the blur shader a plain copy.
            mainFramebuffer.bindFramebuffer(true);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, verticalTexture);
            uniforms.radius.setFloat(0.0F);
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            mainFramebuffer.setFramebufferFilter(previousFilter);
            GLStateManager.glBindVertexArray(previousVao);
            GLStateManager.glUseProgram(previousProgram);
            GLStateManager.popStateTo(stateDepth);
            mainFramebuffer.bindFramebuffer(true);
        }
    }

    /** Replaces one of the two vanilla GUI background gradient colors while the blur is showing. */
    public static int getBackgroundColor(int vanillaColor, String configuredColor) {
        if (!blurring) return vanillaColor;

        final int color;
        try {
            color = Integer.parseUnsignedInt(configuredColor, 16);
        } catch (NumberFormatException e) {
            return vanillaColor;
        }

        final float progress = getProgress();
        final int alpha = (int) ((color >>> 24) * progress);
        final int red = (int) ((color >> 16 & 0xFF) * progress);
        final int green = (int) ((color >> 8 & 0xFF) * progress);
        final int blue = (int) ((color & 0xFF) * progress);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static boolean shouldBlur() {
        return AngelicaConfig.enableMenuBlur
            && mc.theWorld != null
            && mc.currentScreen != null
            && !ArrayUtils.contains(AngelicaConfig.menuBlurExcludedGuis, mc.currentScreen.getClass().getName());
    }

    private static float getProgress() {
        if (AngelicaConfig.menuBlurFadeTime <= 0) return 1.0F;
        return Math.min((System.currentTimeMillis() - fadeStartMillis) / (float) AngelicaConfig.menuBlurFadeTime, 1.0F);
    }

    private static void init() {
        blurProgram = PanoramaRenderer.loadProgram("angelica:menu_blur", "angelica:panorama_blur", "angelica:panorama_blur", BlurUniforms::new);
        allState = StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS);
        emptyVao = GLStateManager.glGenVertexArrays();
    }

    private static void ensureBlurTargets(int width, int height) {
        if (horizontalFbo != 0 && width == sourceWidth && height == sourceHeight) return;

        if (horizontalFbo != 0) {
            GLStateManager.glDeleteFramebuffers(horizontalFbo);
            GLStateManager.glDeleteFramebuffers(verticalFbo);
            GLStateManager.glDeleteTextures(horizontalTexture);
            GLStateManager.glDeleteTextures(verticalTexture);
        }
        sourceWidth = width;
        sourceHeight = height;
        halfWidth = Math.max((width + 1) / 2, 1);
        halfHeight = Math.max((height + 1) / 2, 1);

        horizontalTexture = createTexture(halfWidth, halfHeight);
        horizontalFbo = createFramebuffer(horizontalTexture);
        verticalTexture = createTexture(halfWidth, halfHeight);
        verticalFbo = createFramebuffer(verticalTexture);
    }

    private static int createTexture(int width, int height) {
        final int texture = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static int createFramebuffer(int colorTexture) {
        final int fbo = GLStateManager.glGenFramebuffers();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);
        final int status = GLStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Menu blur FBO incomplete: 0x" + Integer.toHexString(status));
        }
        return fbo;
    }

    private static class BlurUniforms {
        final GlUniformFloat2v direction;
        final GlUniformFloat radius;

        BlurUniforms(ShaderBindingContext ctx) {
            direction = ctx.bindUniform("u_Direction", GlUniformFloat2v::new);
            radius = ctx.bindUniform("u_Radius", GlUniformFloat::new);
        }
    }
}
