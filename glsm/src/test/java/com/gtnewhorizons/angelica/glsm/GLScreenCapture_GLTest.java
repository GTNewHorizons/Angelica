package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.backend.ScreenCapture;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class GLScreenCapture_GLTest {

    private static final int SRC_W = 64;
    private static final int SRC_H = 32;
    private static final int DST_W = 16;
    private static final int DST_H = 8;

    private record Target(int texture, int framebuffer) {
        void delete() {
            GLSMFramebuffer.glDeleteFramebuffers(framebuffer);
            GLSMTextures.glDeleteTextures(texture);
        }
    }

    private static Target quadrantTarget() {
        final int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SRC_W, SRC_H, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        final int framebuffer = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GlTestSupport.fillQuadrants(SRC_W, SRC_H);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return new Target(texture, framebuffer);
    }

    @Test
    void downscalesFramebufferQuadrantsBottomUp() {
        final Target target = quadrantTarget();
        final ScreenCapture capture = GLSMFramebuffer.createScreenCapture(DST_W, DST_H);
        try {
            assertTrue(capture.begin(target.framebuffer(), SRC_W, SRC_H, 42L));
            GL11.glFinish();
            assertTrue(capture.poll());
            assertEquals(42L, capture.tag());
            GlTestSupport.assertQuadrantsBottomUp(capture.address(), DST_W, DST_H);
            capture.release();
        } finally {
            capture.delete();
            target.delete();
        }
    }

    @Test
    void ringRefusesWhenFullAndReturnsInBeginOrder() {
        final Target target = quadrantTarget();
        final ScreenCapture capture = GLSMFramebuffer.createScreenCapture(DST_W, DST_H);
        try {
            for (int i = 0; i < ScreenCapture.RING; i++) assertTrue(capture.begin(target.framebuffer(), SRC_W, SRC_H, i), "capture " + i + " was not recorded");
            assertFalse(capture.begin(target.framebuffer(), SRC_W, SRC_H, 99L), "a full ring must refuse a new capture");
            GL11.glFinish();
            for (int i = 0; i < ScreenCapture.RING; i++) {
                assertTrue(capture.poll(), "capture " + i + " not ready after glFinish");
                assertEquals(i, capture.tag(), "captures must come back in begin order");
                capture.release();
            }
            assertFalse(capture.poll());
            assertTrue(capture.idle());
            assertTrue(capture.begin(target.framebuffer(), SRC_W, SRC_H, 100L), "a drained ring must accept captures again");
        } finally {
            capture.delete();
            target.delete();
        }
    }

    @Test
    void restoresBindingsAndPixelStore() {
        final Target target = quadrantTarget();
        final int readFbo = GLSMFramebuffer.glGenFramebuffers();
        final int drawFbo = GLSMFramebuffer.glGenFramebuffers();
        final int packBuffer = GLSMBuffers.glGenBuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
        GLSMBuffers.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, 16L, GL15.GL_STREAM_READ);
        GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GLSMEnable.glEnable(GL11.GL_SCISSOR_TEST);

        final ScreenCapture capture = GLSMFramebuffer.createScreenCapture(DST_W, DST_H);
        try {
            assertTrue(capture.begin(target.framebuffer(), SRC_W, SRC_H, 1L));
            GL11.glFinish();
            assertTrue(capture.poll());
            capture.release();

            assertEquals(readFbo, GLSMFramebuffer.getReadFramebuffer());
            assertEquals(drawFbo, GLSMFramebuffer.getDrawFramebuffer());
            assertEquals(packBuffer, GLStateManager.ctx().boundPixelPackBuffer);
            assertEquals(1, GLStateManager.ctx().pixelPackState.alignment());
            assertTrue(GLSMRasterizer.getScissorTest().isEnabled());
            assertEquals(readFbo, GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));
            assertEquals(drawFbo, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            assertEquals(packBuffer, GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(1, GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT));
            assertTrue(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
        } finally {
            capture.delete();
            GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
            GLSMTextures.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GLSMBuffers.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GLSMBuffers.glDeleteBuffers(packBuffer);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLSMFramebuffer.glDeleteFramebuffers(readFbo);
            GLSMFramebuffer.glDeleteFramebuffers(drawFbo);
            target.delete();
        }
    }
}
