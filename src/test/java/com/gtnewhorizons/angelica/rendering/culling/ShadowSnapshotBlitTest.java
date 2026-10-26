package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class ShadowSnapshotBlitTest {

    private static final int SIZE = 256;
    private static final float FAR = 1.0f;
    private static final float NEAR_PATCH = 0.25f;

    private int srcTex;
    private int srcFbo;
    private int cacheTex;
    private int cacheFbo;

    @BeforeEach
    void createTargets() {
        srcTex = newDepthTexture();
        srcFbo = newDepthFbo(srcTex);
        cacheTex = newDepthTexture();
        cacheFbo = newDepthFbo(cacheTex);
    }

    @AfterEach
    void deleteTargets() {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLSMFramebuffer.glDeleteFramebuffers(srcFbo);
        GLSMFramebuffer.glDeleteFramebuffers(cacheFbo);
        GLSMTextures.glDeleteTextures(srcTex);
        GLSMTextures.glDeleteTextures(cacheTex);
    }

    private static int newDepthTexture() {
        final int tex = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, SIZE, SIZE, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int newDepthFbo(int tex) {
        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, tex, 0);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GLSMFramebuffer.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER), "depth-only FBO incomplete");
        return fbo;
    }

    private static void fillPattern(int fbo) {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
        GLSMDepthStencilAlpha.glClearDepth(FAR);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, SIZE / 2, SIZE / 2);
        GLSMDepthStencilAlpha.glClearDepth(NEAR_PATCH);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static float readDepth(int fbo, int x, int y) {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        final FloatBuffer buf = BufferUtils.createFloatBuffer(1);
        GLSMFramebuffer.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buf);
        return buf.get(0);
    }

    @Test
    void depthSnapshotRoundTripsThroughBlit() {
        fillPattern(srcFbo);
        assertEquals(NEAR_PATCH, readDepth(srcFbo, 10, 10), 1e-4f, "source pattern not written");
        assertEquals(FAR, readDepth(srcFbo, SIZE - 10, SIZE - 10), 1e-4f, "source background not written");

        RenderSystem.blitFramebuffer(srcFbo, cacheFbo, 0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        assertEquals(NEAR_PATCH, readDepth(cacheFbo, 10, 10), 1e-4f, "capture blit did not copy the near patch into the cache");
        assertEquals(FAR, readDepth(cacheFbo, SIZE - 10, SIZE - 10), 1e-4f, "capture blit did not copy the far background into the cache");

        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, srcFbo);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMDepthStencilAlpha.glClearDepth(FAR);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        assertEquals(FAR, readDepth(srcFbo, 10, 10), 1e-4f, "source not cleared before restore");

        RenderSystem.blitFramebuffer(cacheFbo, srcFbo, 0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        assertEquals(NEAR_PATCH, readDepth(srcFbo, 10, 10), 1e-4f, "restore blit did not bring the near patch back");
        assertEquals(FAR, readDepth(srcFbo, SIZE - 10, SIZE - 10), 1e-4f, "restore blit corrupted the far background");
    }

    @Test
    void depthBlitIsNotAffectedByScissorState() {
        fillPattern(srcFbo);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, cacheFbo);
        GLSMDepthStencilAlpha.glClearDepth(FAR);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, 4, 4);
        RenderSystem.blitFramebuffer(srcFbo, cacheFbo, 0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);

        assertEquals(NEAR_PATCH, readDepth(cacheFbo, 100, 100), 1e-4f, "scissor clipped the snapshot blit; the cache only received the scissor box");
    }

    @Test
    void rawBlitStaysScissorClipped() {
        fillPattern(srcFbo);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, cacheFbo);
        GLSMDepthStencilAlpha.glClearDepth(FAR);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, cacheFbo);
        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);
        GLSMRasterizer.glScissor(0, 0, 4, 4);
        GLSMFramebuffer.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);

        assertEquals(NEAR_PATCH, readDepth(cacheFbo, 1, 1), 1e-4f, "raw blit copied nothing inside the scissor box");
        assertEquals(FAR, readDepth(cacheFbo, 100, 100), 1e-4f, "raw glBlitFramebuffer ignored scissor");
    }
}
