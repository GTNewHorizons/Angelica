package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_CoreRemovedCaps_GLTest {

    static Stream<Arguments> removedCaps() {
        return Stream.of(
            cap("GL_LINE_STIPPLE", GL11.GL_LINE_STIPPLE),
            cap("GL_POINT_SMOOTH", GL11.GL_POINT_SMOOTH),
            cap("GL_POLYGON_STIPPLE", GL11.GL_POLYGON_STIPPLE),
            cap("GL_INDEX_LOGIC_OP", GL11.GL_INDEX_LOGIC_OP),
            cap("GL_AUTO_NORMAL", GL11.GL_AUTO_NORMAL),
            cap("GL_MAP1_COLOR_4", GL11.GL_MAP1_COLOR_4),
            cap("GL_MAP1_INDEX", GL11.GL_MAP1_INDEX),
            cap("GL_MAP1_NORMAL", GL11.GL_MAP1_NORMAL),
            cap("GL_MAP1_TEXTURE_COORD_1", GL11.GL_MAP1_TEXTURE_COORD_1),
            cap("GL_MAP1_TEXTURE_COORD_2", GL11.GL_MAP1_TEXTURE_COORD_2),
            cap("GL_MAP1_TEXTURE_COORD_3", GL11.GL_MAP1_TEXTURE_COORD_3),
            cap("GL_MAP1_TEXTURE_COORD_4", GL11.GL_MAP1_TEXTURE_COORD_4),
            cap("GL_MAP1_VERTEX_3", GL11.GL_MAP1_VERTEX_3),
            cap("GL_MAP1_VERTEX_4", GL11.GL_MAP1_VERTEX_4),
            cap("GL_MAP2_COLOR_4", GL11.GL_MAP2_COLOR_4),
            cap("GL_MAP2_INDEX", GL11.GL_MAP2_INDEX),
            cap("GL_MAP2_NORMAL", GL11.GL_MAP2_NORMAL),
            cap("GL_MAP2_TEXTURE_COORD_1", GL11.GL_MAP2_TEXTURE_COORD_1),
            cap("GL_MAP2_TEXTURE_COORD_2", GL11.GL_MAP2_TEXTURE_COORD_2),
            cap("GL_MAP2_TEXTURE_COORD_3", GL11.GL_MAP2_TEXTURE_COORD_3),
            cap("GL_MAP2_TEXTURE_COORD_4", GL11.GL_MAP2_TEXTURE_COORD_4),
            cap("GL_MAP2_VERTEX_3", GL11.GL_MAP2_VERTEX_3),
            cap("GL_MAP2_VERTEX_4", GL11.GL_MAP2_VERTEX_4));
    }

    private static Arguments cap(String name, int value) {
        return Arguments.of(name, value);
    }

    private static void assertNoGlError(String what) {
        final int error = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, error, () -> what + " raised 0x" + Integer.toHexString(error));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removedCaps")
    void enableDisableIsCacheOnly(String name, int cap) {
        try {
            GLStateManager.glEnable(cap);
            assertNoGlError("glEnable(" + name + ")");
            assertTrue(GLStateManager.glIsEnabled(cap), name + " must still round-trip through the cache");

            GLStateManager.glDisable(cap);
            assertNoGlError("glDisable(" + name + ")");
            assertFalse(GLStateManager.glIsEnabled(cap));
        } finally {
            GLStateManager.glDisable(cap);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removedCaps")
    void pushPopAttribIsCacheOnly(String name, int cap) {
        try {
            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLStateManager.glEnable(cap);
            GLStateManager.glPopAttrib();
            assertNoGlError("glPushAttrib/glEnable(" + name + ")/glPopAttrib");
            assertFalse(GLStateManager.glIsEnabled(cap));
        } finally {
            GLStateManager.glDisable(cap);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
        }
    }

    @Test
    void displayListReplayIsCacheOnly() {
        final int list = GLStateManager.glGenLists(1);
        try {
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            GLStateManager.glEnable(GL11.GL_LINE_STIPPLE);
            GLStateManager.glLineStipple(2, (short) 0xF0F0);
            GLStateManager.glDisable(GL11.GL_LINE_STIPPLE);
            GLStateManager.glEndList();
            assertNoGlError("display list compile");

            GLStateManager.glCallList(list);
            assertNoGlError("display list replay");
        } finally {
            GLStateManager.glDeleteLists(list, 1);
            GLStateManager.glDisable(GL11.GL_LINE_STIPPLE);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
        }
    }
}
