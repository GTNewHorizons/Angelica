package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.LegacyQueryFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.CLIENT_ARRAY_CAPS;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.HINT_TARGETS;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.assertEnableRoundTrip;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.drainClientAttribStack;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.hex;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.popAttribTo;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.setEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlLegacyQueryTest {

    private static final int[] CACHE_ONLY_CAPS = { GL20.GL_POINT_SPRITE, GL20.GL_VERTEX_PROGRAM_TWO_SIDE, GL13.GL_TEXTURE_CUBE_MAP };

    // The SDL backend answers 0 or false for a pname it does not know, so a query that fell through to it
    // fails the non-zero expectations below.
    @Test
    void legacyQueriesAreAnsweredWithoutTheBackend() {
        final int base = GLStateManager.getAttribDepth();
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(16);
        final ByteBuffer bytes = BufferUtils.createByteBuffer(16);
        final FloatBuffer positions = BufferUtils.createFloatBuffer(12);
        final FloatBuffer other = BufferUtils.createFloatBuffer(12);
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        final boolean savedLighting = GLStateManager.glIsEnabled(GL11.GL_LIGHTING);
        final boolean savedEdge = GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG);
        final int[] savedHints = new int[HINT_TARGETS.length];
        for (int i = 0; i < HINT_TARGETS.length; i++) {
            savedHints[i] = GLStateManager.glGetInteger(HINT_TARGETS[i]);
        }
        try {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(0);
            assertEquals(1, GLStateManager.getClientActiveTextureUnit(), "an invalid unit is rejected");
            assertEquals(GLStateManager.MAX_TEXTURE_COORDS, GLStateManager.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);

            for (final int cap : CLIENT_ARRAY_CAPS) {
                final boolean was = GLStateManager.glIsEnabled(cap);
                assertEnableRoundTrip(cap, true);
                if (was) GLStateManager.glEnableClientState(cap);
            }
            for (final int cap : CACHE_ONLY_CAPS) {
                final boolean was = GLStateManager.glIsEnabled(cap);
                assertEnableRoundTrip(cap, false);
                setEnabled(cap, was);
            }
            GLStateManager.glEdgeFlag(false);
            assertFalse(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG));
            GLStateManager.glEdgeFlag(true);
            assertTrue(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG));

            for (final int target : HINT_TARGETS) {
                final String name = hex(target);
                GLStateManager.glHint(target, GL11.GL_NICEST);
                assertEquals(GL11.GL_NICEST, GLStateManager.glGetInteger(target), "set " + name);
                assertEquals((float) GL11.GL_NICEST, GLStateManager.glGetFloat(target), "float " + name);
                GLStateManager.glPushAttrib(GL11.GL_HINT_BIT);
                GLStateManager.glHint(target, GL11.GL_FASTEST);
                assertEquals(GL11.GL_FASTEST, GLStateManager.glGetInteger(target), "inside push " + name);
                GLStateManager.glPopAttrib();
                assertEquals(GL11.GL_NICEST, GLStateManager.glGetInteger(target), "after pop " + name);
            }

            GLStateManager.glVertexPointer(3, 12, positions);
            GLStateManager.glFogCoordPointer(4, positions);
            assertEquals(3, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE));
            assertEquals(GL11.GL_FLOAT, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_TYPE));
            assertEquals(12, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE));
            assertEquals(0, GLStateManager.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));
            assertEquals(4, GLStateManager.glGetInteger(GL14.GL_FOG_COORDINATE_ARRAY_STRIDE));
            assertEquals(3, GLStateManager.glGetInteger(GL14.GL_SECONDARY_COLOR_ARRAY_SIZE));
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glVertexPointer(2, 8, other);
            GLStateManager.glFogCoordPointer(8, other);
            GLStateManager.glEnableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            assertEquals(2, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE));
            GLStateManager.glPopClientAttrib();
            assertEquals(3, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE), "client attrib pop restores the pointer");
            assertEquals(12, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE));
            assertEquals(4, GLStateManager.glGetInteger(GL14.GL_FOG_COORDINATE_ARRAY_STRIDE));
            assertFalse(GLStateManager.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY));

            GLStateManager.glColor4f(1.0f, 0.5f, 0.0f, 1.0f);
            GLStateManager.glGetInteger(GL11.GL_CURRENT_COLOR, ints);
            assertEquals(2147483647, ints.get(0));
            assertEquals(1073741823, ints.get(1));
            assertEquals(0, ints.get(2));
            GLStateManager.glEnable(GL11.GL_LIGHTING);
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_LIGHTING));
            assertEquals(1.0f, GLStateManager.glGetFloat(GL11.GL_LIGHTING));
            assertEquals((float) GLStateManager.glGetInteger(GL11.GL_FOG_MODE), GLStateManager.glGetFloat(GL11.GL_FOG_MODE));
            assertEquals(GL11.GL_RENDER, GLStateManager.glGetInteger(GL11.GL_RENDER_MODE));

            for (final int pname : LegacyQueryFixture.legacyPnames()) {
                final String name = hex(pname);
                final int scalar = GLStateManager.glGetInteger(pname);
                final float scalarFloat = GLStateManager.glGetFloat(pname);
                final boolean scalarBool = GLStateManager.glGetBoolean(pname);
                ints.clear();
                GLStateManager.glGetInteger(pname, ints);
                floats.clear();
                GLStateManager.glGetFloat(pname, floats);
                doubles.clear();
                GLStateManager.glGetDouble(pname, doubles);
                bytes.clear();
                GLStateManager.glGetBoolean(pname, bytes);
                assertEquals(scalar, ints.get(0), "IntBuffer overload for " + name);
                assertEquals(scalarFloat, floats.get(0), "FloatBuffer overload for " + name);
                assertEquals((double) scalarFloat, doubles.get(0), "DoubleBuffer overload for " + name);
                assertEquals(scalarBool, bytes.get(0) != 0, "ByteBuffer overload for " + name);
            }
        } finally {
            drainClientAttribStack();
            popAttribTo(base);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDisableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            for (int i = 0; i < HINT_TARGETS.length; i++) {
                GLStateManager.glHint(HINT_TARGETS[i], savedHints[i]);
            }
            GLStateManager.glEdgeFlag(savedEdge);
            GLStateManager.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
            setEnabled(GL11.GL_LIGHTING, savedLighting);
        }
    }

    @Test
    void pointParametersRoundTrip() {
        final FloatBuffer attenuation = BufferUtils.createFloatBuffer(16);
        final float savedMin = GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN);
        final float savedMax = GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MAX);
        final float savedThreshold = GLStateManager.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE);
        final int savedOrigin = GLStateManager.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN);
        final int base = GLStateManager.getAttribDepth();
        try {
            assertEquals(1.0f, savedMax);
            GLStateManager.glPushAttrib(GL11.GL_POINT_BIT);
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MIN, 2.0f);
            GLStateManager.glPointParameterf(GL14.GL_POINT_SIZE_MAX, 8.0f);
            GLStateManager.glPointParameterf(GL14.GL_POINT_FADE_THRESHOLD_SIZE, 3.0f);
            GLStateManager.glPointParameteri(GL20.GL_POINT_SPRITE_COORD_ORIGIN, GL20.GL_LOWER_LEFT);
            attenuation.clear();
            attenuation.put(0, 0.5f).put(1, 0.25f).put(2, 0.125f);
            GLStateManager.glPointParameter(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            assertEquals(2.0f, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(8.0f, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MAX));
            assertEquals(3.0f, GLStateManager.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertEquals(GL20.GL_LOWER_LEFT, GLStateManager.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            attenuation.clear();
            GLStateManager.glGetFloat(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            assertEquals(0.25f, attenuation.get(1));
            GLStateManager.glPopAttrib();
            assertEquals(savedMin, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(savedMax, GLStateManager.glGetFloat(GL14.GL_POINT_SIZE_MAX));
            assertEquals(savedThreshold, GLStateManager.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertEquals(savedOrigin, GLStateManager.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
        } finally {
            popAttribTo(base);
        }
    }
}
