package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.RED;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.assertUniform;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.createSolidMipTexture;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.readTextureLevel;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlMipAttachmentTest {

    private static final int TEX_SIZE = 64;
    private static final int MAX_LEVEL = 6;
    private static final int CLEAR = 0xFF204080;
    private static final int BASE = 0xFF10C030;

    private static void setClearColor(int argb) {
        GLSMBlend.glClearColor(((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f, ((argb >>> 24) & 0xFF) / 255.0f);
    }

    private static int levelColor(int level) {
        return (CLEAR + level) | 0xFF000000;
    }

    @Test
    void clearingEachLevelWritesOnlyThatLevel() {
        final int texture = createSolidMipTexture(TEX_SIZE, MAX_LEVEL, BASE);
        final int fbo = GlsmSdlHeadlessRig.fboWithColor(texture, 0);

        for (int level = 1; level <= MAX_LEVEL; level++) {
            final int extent = Math.max(1, TEX_SIZE >> level);
            GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, level);
            GLSMRasterizer.glViewport(0, 0, extent, extent);
            setClearColor(levelColor(level));
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        for (int level = 1; level <= MAX_LEVEL; level++) {
            assertUniform(readTextureLevel(texture, level), levelColor(level), "level " + level + " after clearing its own attachment");
        }
        assertUniform(readTextureLevel(texture, 0), BASE, "level 0 untouched by clearing the mip levels");

        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 1);
        final int w = Math.max(1, TEX_SIZE >> 1);
        final ByteBuffer pixels = MemoryUtil.memAlloc(w * w * 4);
        try {
            GLSMFramebuffer.glReadPixels(0, 0, w, w, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            assertUniform(GlsmSdlHeadlessRig.toArgb(pixels, w * w), levelColor(1), "readPixels of the level 1 attachment");
        } finally {
            MemoryUtil.memFree(pixels);
        }

        GLSMFramebuffer.glDeleteFramebuffers(fbo);
        GLSMTextures.glDeleteTextures(texture);
    }

    @Test
    void pendingLevelZeroClearIsNotAppliedToALevelOnePass() {
        final int texture = createSolidMipTexture(TEX_SIZE, MAX_LEVEL, BASE);
        final int fbo = GlsmSdlHeadlessRig.fboWithColor(texture, 0);
        GLSMRasterizer.glViewport(0, 0, TEX_SIZE, TEX_SIZE);
        setClearColor(CLEAR);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 1);
        GLSMRasterizer.glViewport(0, 0, TEX_SIZE / 2, TEX_SIZE / 2);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        assertUniform(readTextureLevel(texture, 1), RED, "level 1 after drawing into it");
        assertUniform(readTextureLevel(texture, 0), CLEAR, "level 0 keeps its own pending clear");

        GLSMFramebuffer.glDeleteFramebuffers(fbo);
        GLSMTextures.glDeleteTextures(texture);
    }
}
