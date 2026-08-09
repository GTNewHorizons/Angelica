package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
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

    private static int newDepthTexture() {
        final int tex = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, SIZE, SIZE, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int newDepthFbo(int tex) {
        final int fbo = GLStateManager.glGenFramebuffers();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, tex, 0);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GLStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER), "depth-only FBO incomplete");
        return fbo;
    }

    private static void fillPattern(int fbo) {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glDepthMask(true);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glClearDepth(FAR);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, 0, SIZE / 2, SIZE / 2);
        GLStateManager.glClearDepth(NEAR_PATCH);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static float readDepth(int fbo, int x, int y) {
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        final FloatBuffer buf = BufferUtils.createFloatBuffer(1);
        GLStateManager.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buf);
        return buf.get(0);
    }

    @Test
    void depthSnapshotRoundTripsThroughBlit() {
        final int srcTex = newDepthTexture();
        final int srcFbo = newDepthFbo(srcTex);
        final int cacheTex = newDepthTexture();
        final int cacheFbo = newDepthFbo(cacheTex);
        try {
            fillPattern(srcFbo);
            assertEquals(NEAR_PATCH, readDepth(srcFbo, 10, 10), 1e-4f, "source pattern not written");
            assertEquals(FAR, readDepth(srcFbo, SIZE - 10, SIZE - 10), 1e-4f, "source background not written");

            RenderSystem.blitFramebuffer(srcFbo, cacheFbo, 0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            assertEquals(NEAR_PATCH, readDepth(cacheFbo, 10, 10), 1e-4f, "capture blit did not copy the near patch into the cache");
            assertEquals(FAR, readDepth(cacheFbo, SIZE - 10, SIZE - 10), 1e-4f, "capture blit did not copy the far background into the cache");

            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, srcFbo);
            GLStateManager.glDepthMask(true);
            GLStateManager.glClearDepth(FAR);
            GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            assertEquals(FAR, readDepth(srcFbo, 10, 10), 1e-4f, "source not cleared before restore");

            RenderSystem.blitFramebuffer(cacheFbo, srcFbo, 0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            assertEquals(NEAR_PATCH, readDepth(srcFbo, 10, 10), 1e-4f, "restore blit did not bring the near patch back");
            assertEquals(FAR, readDepth(srcFbo, SIZE - 10, SIZE - 10), 1e-4f, "restore blit corrupted the far background");
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLStateManager.glDeleteFramebuffers(srcFbo);
            GLStateManager.glDeleteFramebuffers(cacheFbo);
            GLStateManager.glDeleteTextures(srcTex);
            GLStateManager.glDeleteTextures(cacheTex);
        }
    }

    @Test
    void depthBlitIsNotAffectedByScissorState() {
        final int srcTex = newDepthTexture();
        final int srcFbo = newDepthFbo(srcTex);
        final int cacheTex = newDepthTexture();
        final int cacheFbo = newDepthFbo(cacheTex);
        try {
            fillPattern(srcFbo);
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, cacheFbo);
            GLStateManager.glClearDepth(FAR);
            GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
            GLStateManager.glScissor(0, 0, 4, 4);
            RenderSystem.blitFramebuffer(srcFbo, cacheFbo, 0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);

            assertEquals(NEAR_PATCH, readDepth(cacheFbo, 100, 100), 1e-4f, "scissor clipped the snapshot blit; the cache only received the scissor box");
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLStateManager.glDeleteFramebuffers(srcFbo);
            GLStateManager.glDeleteFramebuffers(cacheFbo);
            GLStateManager.glDeleteTextures(srcTex);
            GLStateManager.glDeleteTextures(cacheTex);
        }
    }

    @Test
    void rawBlitStaysScissorClipped() {
        final int srcTex = newDepthTexture();
        final int srcFbo = newDepthFbo(srcTex);
        final int cacheTex = newDepthTexture();
        final int cacheFbo = newDepthFbo(cacheTex);
        try {
            fillPattern(srcFbo);
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, cacheFbo);
            GLStateManager.glClearDepth(FAR);
            GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, cacheFbo);
            GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
            GLStateManager.glScissor(0, 0, 4, 4);
            GLStateManager.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);

            assertEquals(NEAR_PATCH, readDepth(cacheFbo, 1, 1), 1e-4f, "raw blit copied nothing inside the scissor box");
            assertEquals(FAR, readDepth(cacheFbo, 100, 100), 1e-4f, "raw glBlitFramebuffer ignored scissor");
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLStateManager.glDeleteFramebuffers(srcFbo);
            GLStateManager.glDeleteFramebuffers(cacheFbo);
            GLStateManager.glDeleteTextures(srcTex);
            GLStateManager.glDeleteTextures(cacheTex);
        }
    }
}
