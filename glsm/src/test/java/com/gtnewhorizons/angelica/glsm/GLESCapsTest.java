package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLESCapsTest {

    private static final int[] DESKTOP_ONLY = {
        GL11.GL_LINE_SMOOTH,
        GL11.GL_POLYGON_SMOOTH,
        GL13.GL_MULTISAMPLE,
        GL13.GL_SAMPLE_ALPHA_TO_ONE,
        GL11.GL_COLOR_LOGIC_OP,
        GL11.GL_POLYGON_OFFSET_POINT,
        GL11.GL_POLYGON_OFFSET_LINE,
        GL20.GL_VERTEX_PROGRAM_POINT_SIZE,
        GL30.GL_FRAMEBUFFER_SRGB,
        GL31.GL_PRIMITIVE_RESTART,
        GL32.GL_DEPTH_CLAMP,
    };

    private static final int[] VALID_ON_GLES = {
        GL11.GL_BLEND,
        GL11.GL_DEPTH_TEST,
        GL11.GL_CULL_FACE,
        GL11.GL_SCISSOR_TEST,
        GL11.GL_STENCIL_TEST,
        GL11.GL_DITHER,
        GL11.GL_POLYGON_OFFSET_FILL,
        GL13.GL_SAMPLE_COVERAGE,
        GL13.GL_SAMPLE_ALPHA_TO_COVERAGE,
    };

    private static boolean onGles(int cap) {
        return GLESCaps.isCapAllowed(cap, true, true);
    }

    @Test
    void desktopOnlyCapsAreSuppressedOnGles() {
        for (int cap : DESKTOP_ONLY) {
            assertFalse(onGles(cap), () -> "cap 0x" + Integer.toHexString(cap) + " raises GL_INVALID_ENUM on a GLES context and must not reach the driver");
        }
    }

    @Test
    void capsValidOnGlesStillReachTheDriver() {
        for (int cap : VALID_ON_GLES) {
            assertTrue(onGles(cap), () -> "cap 0x" + Integer.toHexString(cap) + " is legal on GLES 3.2 and suppressing it would break rendering");
        }
    }

    @Test
    void desktopContextAllowsEveryCap() {
        for (int cap : DESKTOP_ONLY) {
            assertTrue(GLESCaps.isCapAllowed(cap, false, false), () -> "cap 0x" + Integer.toHexString(cap) + " is legal on desktop core and the gate must never suppress it there");
        }
        for (int cap : VALID_ON_GLES) {
            assertTrue(GLESCaps.isCapAllowed(cap, false, false));
        }
    }

    @Test
    void clipPlanesFollowClipCullDistance() {
        for (int i = 0; i < GLStateManager.MAX_CLIP_PLANES; i++) {
            final int cap = GL11.GL_CLIP_PLANE0 + i;
            assertTrue(GLESCaps.isCapAllowed(cap, true, true), () -> "clip plane " + cap + " works where EXT_clip_cull_distance is present and must not be suppressed");
            assertFalse(GLESCaps.isCapAllowed(cap, true, false), () -> "clip plane " + cap + " is invalid on GLES without EXT_clip_cull_distance");
        }
    }

    @Test
    void capsOutsideTheClipPlaneRangeAreUnaffectedByClipCullDistance() {
        assertTrue(GLESCaps.isCapAllowed(GL11.GL_BLEND, true, false));
        assertTrue(GLESCaps.isCapAllowed(GL11.GL_CLIP_PLANE0 + GLStateManager.MAX_CLIP_PLANES, true, false));
    }
}
