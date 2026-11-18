package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.BLUE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.GREEN;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.RED;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlFbo0ViewportTest {

    private static final int FBO0_W = 64;
    private static final int FBO0_H = 96;
    private static final int VIEW = 32;

    @BeforeAll
    static void installFinalTarget() {
        GlsmSdlHeadlessRig.installFinalTarget(FBO0_W, FBO0_H);
    }

    @AfterAll
    static void dropFinalTarget() {
        GlsmSdlHeadlessRig.dropFinalTarget();
    }

    private static void bindFbo0WithSmallViewport() {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLSMRasterizer.glViewport(0, 0, VIEW, VIEW);
        GLSMBlend.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private static void bindFbo0WithFullViewport() {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLSMRasterizer.glViewport(0, 0, FBO0_W, FBO0_H);
        GLSMBlend.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private static int createCopyTarget() {
        final int id = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, VIEW, VIEW, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return id;
    }

    private static void drawTextureOverTarget(int texture) {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.texturedQuad(texture, 1.0f);
    }

    @Test
    void partialViewportDrawCoversOnlyTheGlViewportRows() {
        bindFbo0WithSmallViewport();
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(0, 0, VIEW, VIEW), RED, "inside the viewport");
        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(0, FBO0_H - VIEW, VIEW, VIEW), BLUE, "above the viewport");
    }

    @Test
    void scissoredClearFlipsYOnFbo0() {
        bindFbo0WithFullViewport();
        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, FBO0_W, VIEW);
        GLSMBlend.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);

        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(0, 0, FBO0_W, VIEW), RED, "GL scissor y=0 is the bottom rows of the FBO0 texture");
        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(0, FBO0_H - VIEW, FBO0_W, VIEW), BLUE, "the top rows must stay untouched by the flipped scissor");
    }

    @Test
    void scissorLargerThanFbo0StillCoversTheWholeTarget() {
        bindFbo0WithFullViewport();
        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        try {
            GLSMRasterizer.glScissor(0, 0, FBO0_W + 16, FBO0_H + 16);
            GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(0, 0, FBO0_W, FBO0_H), RED, "inside an oversize scissor");
        } finally {
            GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    @Test
    void scissorRunningPastTheRightEdgeClipsToTheTarget() {
        bindFbo0WithFullViewport();
        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        try {
            GLSMRasterizer.glScissor(FBO0_W - 8, 0, 32, FBO0_H);
            GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(FBO0_W - 8, 0, 8, FBO0_H), RED, "inside the clipped scissor");
            GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(0, 0, FBO0_W - 8, FBO0_H), BLUE, "left of the scissor");
        } finally {
            GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    @Test
    void copyTexSubImage2DFromAPartialViewportKeepsGlOrientation() {
        bindFbo0WithSmallViewport();
        GlsmSdlHeadlessRig.halfQuad(-1.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        GlsmSdlHeadlessRig.halfQuad(0.0f, 1.0f, 1.0f, 0.0f, 0.0f);

        final int copy = createCopyTarget();
        GLSMTextures.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, VIEW, VIEW);

        drawTextureOverTarget(copy);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, 4, GREEN, "sampled copy bottom");
        GlsmSdlHeadlessRig.assertPixel(pixels, SIZE, SIZE / 2, SIZE - 5, RED, "sampled copy top");
    }

    @Test
    void copyTexImage2DAllocatesTheDestinationLevel() {
        bindFbo0WithSmallViewport();
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        final int copy = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, copy);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        assertNull(GlsmSdlHeadlessRig.resourceManager().getTextureMeta(copy), "destination was allocated before the copy");

        GLSMTextures.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 0, 0, VIEW, VIEW, 0);

        final ResourceManager.TextureMeta meta = GlsmSdlHeadlessRig.resourceManager().getTextureMeta(copy);
        assertNotNull(meta, "copyTexImage2D did not allocate the destination");
        assertEquals(VIEW, meta.width());
        assertEquals(VIEW, meta.height());

        GlsmSdlHeadlessRig.endFrame();
        final ByteBuffer out = MemoryUtil.memAlloc(VIEW * VIEW * 4);
        try {
            GLSMTextures.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, out);
            for (int i = 0; i < VIEW * VIEW; i++) {
                final int idx = i;
                assertEquals((byte) 0xFF, out.get(i * 4), () -> "R at texel " + idx);
                assertEquals((byte) 0x00, out.get(i * 4 + 1), () -> "G at texel " + idx);
                assertEquals((byte) 0x00, out.get(i * 4 + 2), () -> "B at texel " + idx);
                assertEquals((byte) 0xFF, out.get(i * 4 + 3), () -> "A at texel " + idx);
            }
        } finally {
            MemoryUtil.memFree(out);
        }
    }
}
