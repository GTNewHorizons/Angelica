package com.gtnewhorizons.angelica.blur;

import com.gtnewhorizons.angelica.client.rendering.GlUniformFloat2v;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
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

        final int previousProgram = GLSMPrograms.getActiveProgram();
        final int previousVao = GLSMQueries.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int previousFilter = mainFramebuffer.framebufferFilter;
        final int stateDepth = GLSMAttribStack.pushState(allState);
        try {
            ensureBlurTargets(mainFramebuffer.framebufferWidth, mainFramebuffer.framebufferHeight);

            GLSMBlend.disableBlend();
            GLSMDepthStencilAlpha.disableDepthTest();
            GLSMDepthStencilAlpha.glDepthMask(false);
            GLSMBlend.glColorMask(true, true, true, true);
            GLSMRasterizer.disableCull();
            GLSMRasterizer.disableScissorTest();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);

            blurProgram.bind();
            GLSMVertexInput.glBindVertexArray(emptyVao);
            final BlurUniforms uniforms = blurProgram.getInterface();
            // Each half resolution pixel covers two screen pixels, so half the radius gives the same strength
            uniforms.radius.setFloat(radius * 0.5F);

            // Horizontal pass, shrinking the screen to half size. Linear filtering makes every
            // half size pixel average the four screen pixels it covers instead of picking one.
            mainFramebuffer.setFramebufferFilter(GL11.GL_LINEAR);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, horizontalFbo);
            GLSMRasterizer.glViewport(0, 0, halfWidth, halfHeight);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, mainFramebuffer.framebufferTexture);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            uniforms.direction.set(1.0F / halfWidth, 0.0F);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // Vertical pass, still at half size
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, verticalFbo);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, horizontalTexture);
            uniforms.direction.set(0.0F, 1.0F / halfHeight);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            // Stretch the result back over the screen. A radius of 0 makes the blur shader a plain copy.
            mainFramebuffer.bindFramebuffer(true);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, verticalTexture);
            uniforms.radius.setFloat(0.0F);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            mainFramebuffer.setFramebufferFilter(previousFilter);
            GLSMVertexInput.glBindVertexArray(previousVao);
            GLSMPrograms.glUseProgram(previousProgram);
            GLSMAttribStack.popStateTo(stateDepth);
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
        emptyVao = GLSMVertexInput.glGenVertexArrays();
    }

    private static void ensureBlurTargets(int width, int height) {
        if (horizontalFbo != 0 && width == sourceWidth && height == sourceHeight) return;

        if (horizontalFbo != 0) {
            GLSMFramebuffer.glDeleteFramebuffers(horizontalFbo);
            GLSMFramebuffer.glDeleteFramebuffers(verticalFbo);
            GLSMTextures.glDeleteTextures(horizontalTexture);
            GLSMTextures.glDeleteTextures(verticalTexture);
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
        final int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static int createFramebuffer(int colorTexture) {
        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);
        final int status = GLSMFramebuffer.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
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
