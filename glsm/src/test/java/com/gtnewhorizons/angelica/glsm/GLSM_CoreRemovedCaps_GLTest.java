package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;
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
            cap("GL_COLOR_SUM", GL14.GL_COLOR_SUM),
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

    private static void assertSecondaryColor(float red, float green, float blue) {
        final FloatBuffer params = BufferUtils.createFloatBuffer(4);
        GLSMQueries.glGetFloat(GL14.GL_CURRENT_SECONDARY_COLOR, params);
        assertEquals(red, params.get(0), "red must round-trip through the cache");
        assertEquals(green, params.get(1), "green must round-trip through the cache");
        assertEquals(blue, params.get(2), "blue must round-trip through the cache");
    }

    private static void assertNoGlError(String what) {
        final int error = GL11.glGetError();
        assertEquals(GL11.GL_NO_ERROR, error, () -> what + " raised 0x" + Integer.toHexString(error));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removedCaps")
    void enableDisableIsCacheOnly(String name, int cap) {
        try {
            GLSMEnable.glEnable(cap);
            assertNoGlError("glEnable(" + name + ")");
            assertTrue(GLSMEnable.glIsEnabled(cap), name + " must still round-trip through the cache");

            GLSMEnable.glDisable(cap);
            assertNoGlError("glDisable(" + name + ")");
            assertFalse(GLSMEnable.glIsEnabled(cap));
        } finally {
            GLSMEnable.glDisable(cap);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removedCaps")
    void pushPopAttribIsCacheOnly(String name, int cap) {
        try {
            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMEnable.glEnable(cap);
            GLSMAttribStack.glPopAttrib();
            assertNoGlError("glPushAttrib/glEnable(" + name + ")/glPopAttrib");
            assertFalse(GLSMEnable.glIsEnabled(cap));
        } finally {
            GLSMEnable.glDisable(cap);
        }
    }

    @Test
    void secondaryColorIsRestoredByPopAttrib() {
        try {
            GLSMCurrentAttribs.glSecondaryColor3f(0.125F, 0.25F, 0.375F);
            GLSMAttribStack.glPushAttrib(GL11.GL_CURRENT_BIT);
            GLSMCurrentAttribs.glSecondaryColor3f(1.0F, 1.0F, 1.0F);
            GLSMAttribStack.glPopAttrib();
            assertNoGlError("glPushAttrib/glSecondaryColor3f/glPopAttrib");

            assertSecondaryColor(0.125F, 0.25F, 0.375F);
        } finally {
            GLSMCurrentAttribs.glSecondaryColor3f(0.0F, 0.0F, 0.0F);
        }
    }

    @Test
    void colorSumIsRestoredByFogBitPopAttrib() {
        try {
            GLSMAttribStack.glPushAttrib(GL11.GL_FOG_BIT);
            GLSMEnable.glEnable(GL14.GL_COLOR_SUM);
            GLSMAttribStack.glPopAttrib();
            assertNoGlError("glPushAttrib(GL_FOG_BIT)/glEnable(GL_COLOR_SUM)/glPopAttrib");
            assertFalse(GLSMEnable.glIsEnabled(GL14.GL_COLOR_SUM));
        } finally {
            GLSMEnable.glDisable(GL14.GL_COLOR_SUM);
        }
    }

    @Test
    void secondaryColorReplaysFromDisplayList() {
        final int list = GLSMDraws.glGenLists(1);
        try {
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMCurrentAttribs.glSecondaryColor3f(0.5F, 0.625F, 0.75F);
            GLSMDraws.glEndList();
            assertNoGlError("glSecondaryColor3f display list compile");

            GLSMCurrentAttribs.glSecondaryColor3f(0.0F, 0.0F, 0.0F);
            GLSMDraws.glCallList(list);
            assertNoGlError("glSecondaryColor3f display list replay");

            assertSecondaryColor(0.5F, 0.625F, 0.75F);
        } finally {
            GLSMDraws.glDeleteLists(list, 1);
            GLSMCurrentAttribs.glSecondaryColor3f(0.0F, 0.0F, 0.0F);
        }
    }

    @Test
    void displayListReplayIsCacheOnly() {
        final int list = GLSMDraws.glGenLists(1);
        try {
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMEnable.glEnable(GL11.GL_LINE_STIPPLE);
            GLSMRasterizer.glLineStipple(2, (short) 0xF0F0);
            GLSMEnable.glDisable(GL11.GL_LINE_STIPPLE);
            GLSMDraws.glEndList();
            assertNoGlError("display list compile");

            GLSMDraws.glCallList(list);
            assertNoGlError("display list replay");
        } finally {
            GLSMDraws.glDeleteLists(list, 1);
            GLSMEnable.glDisable(GL11.GL_LINE_STIPPLE);
        }
    }
}
