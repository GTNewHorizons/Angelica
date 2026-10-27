package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
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
        final int base = GLSMAttribStack.getAttribDepth();
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
        final DoubleBuffer doubles = BufferUtils.createDoubleBuffer(16);
        final ByteBuffer bytes = BufferUtils.createByteBuffer(16);
        final FloatBuffer positions = BufferUtils.createFloatBuffer(12);
        final FloatBuffer other = BufferUtils.createFloatBuffer(12);
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        GLSMQueries.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        final boolean savedLighting = GLSMEnable.glIsEnabled(GL11.GL_LIGHTING);
        final boolean savedEdge = GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG);
        final int[] savedHints = new int[HINT_TARGETS.length];
        for (int i = 0; i < HINT_TARGETS.length; i++) {
            savedHints[i] = GLSMQueries.glGetInteger(HINT_TARGETS[i]);
        }
        try {
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(GL13.GL_TEXTURE1, GLSMQueries.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLSMTextures.glClientActiveTexture(0);
            assertEquals(1, GLSMTextures.getClientActiveTextureUnit(), "an invalid unit is rejected");
            assertEquals(GLStateManager.MAX_TEXTURE_COORDS, GLSMQueries.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS));
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);

            for (final int cap : CLIENT_ARRAY_CAPS) {
                final boolean was = GLSMEnable.glIsEnabled(cap);
                assertEnableRoundTrip(cap, true);
                if (was) GLSMVertexInput.glEnableClientState(cap);
            }
            for (final int cap : CACHE_ONLY_CAPS) {
                final boolean was = GLSMEnable.glIsEnabled(cap);
                assertEnableRoundTrip(cap, false);
                setEnabled(cap, was);
            }
            GLSMCurrentAttribs.glEdgeFlag(false);
            assertFalse(GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG));
            GLSMCurrentAttribs.glEdgeFlag(true);
            assertTrue(GLSMQueries.glGetBoolean(GL11.GL_EDGE_FLAG));

            for (final int target : HINT_TARGETS) {
                final String name = hex(target);
                GLSMRasterizer.glHint(target, GL11.GL_NICEST);
                assertEquals(GL11.GL_NICEST, GLSMQueries.glGetInteger(target), "set " + name);
                assertEquals((float) GL11.GL_NICEST, GLSMQueries.glGetFloat(target), "float " + name);
                GLSMAttribStack.glPushAttrib(GL11.GL_HINT_BIT);
                GLSMRasterizer.glHint(target, GL11.GL_FASTEST);
                assertEquals(GL11.GL_FASTEST, GLSMQueries.glGetInteger(target), "inside push " + name);
                GLSMAttribStack.glPopAttrib();
                assertEquals(GL11.GL_NICEST, GLSMQueries.glGetInteger(target), "after pop " + name);
            }

            GLSMVertexInput.glVertexPointer(3, 12, positions);
            GLSMVertexInput.glFogCoordPointer(4, positions);
            assertEquals(3, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE));
            assertEquals(GL11.GL_FLOAT, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_TYPE));
            assertEquals(12, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE));
            assertEquals(0, GLSMQueries.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));
            assertEquals(4, GLSMQueries.glGetInteger(GL14.GL_FOG_COORDINATE_ARRAY_STRIDE));
            assertEquals(3, GLSMQueries.glGetInteger(GL14.GL_SECONDARY_COLOR_ARRAY_SIZE));
            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMVertexInput.glVertexPointer(2, 8, other);
            GLSMVertexInput.glFogCoordPointer(8, other);
            GLSMVertexInput.glEnableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            assertEquals(2, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE));
            GLSMAttribStack.glPopClientAttrib();
            assertEquals(3, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE), "client attrib pop restores the pointer");
            assertEquals(12, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE));
            assertEquals(4, GLSMQueries.glGetInteger(GL14.GL_FOG_COORDINATE_ARRAY_STRIDE));
            assertFalse(GLSMEnable.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY));

            GLSMCurrentAttribs.glColor4f(1.0f, 0.5f, 0.0f, 1.0f);
            GLSMQueries.glGetInteger(GL11.GL_CURRENT_COLOR, ints);
            assertEquals(2147483647, ints.get(0));
            assertEquals(1073741823, ints.get(1));
            assertEquals(0, ints.get(2));
            GLSMEnable.glEnable(GL11.GL_LIGHTING);
            assertEquals(1, GLSMQueries.glGetInteger(GL11.GL_LIGHTING));
            assertEquals(1.0f, GLSMQueries.glGetFloat(GL11.GL_LIGHTING));
            assertEquals((float) GLSMQueries.glGetInteger(GL11.GL_FOG_MODE), GLSMQueries.glGetFloat(GL11.GL_FOG_MODE));
            assertEquals(GL11.GL_RENDER, GLSMQueries.glGetInteger(GL11.GL_RENDER_MODE));

            for (final int pname : LegacyQueryFixture.legacyPnames()) {
                final String name = hex(pname);
                final int scalar = GLSMQueries.glGetInteger(pname);
                final float scalarFloat = GLSMQueries.glGetFloat(pname);
                final boolean scalarBool = GLSMQueries.glGetBoolean(pname);
                ints.clear();
                GLSMQueries.glGetInteger(pname, ints);
                floats.clear();
                GLSMQueries.glGetFloat(pname, floats);
                doubles.clear();
                GLSMQueries.glGetDouble(pname, doubles);
                bytes.clear();
                GLSMQueries.glGetBoolean(pname, bytes);
                assertEquals(scalar, ints.get(0), "IntBuffer overload for " + name);
                assertEquals(scalarFloat, floats.get(0), "FloatBuffer overload for " + name);
                assertEquals((double) scalarFloat, doubles.get(0), "DoubleBuffer overload for " + name);
                assertEquals(scalarBool, bytes.get(0) != 0, "ByteBuffer overload for " + name);
            }
        } finally {
            drainClientAttribStack();
            popAttribTo(base);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLSMVertexInput.glDisableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            for (int i = 0; i < HINT_TARGETS.length; i++) {
                GLSMRasterizer.glHint(HINT_TARGETS[i], savedHints[i]);
            }
            GLSMCurrentAttribs.glEdgeFlag(savedEdge);
            GLSMCurrentAttribs.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
            setEnabled(GL11.GL_LIGHTING, savedLighting);
        }
    }

    @Test
    void pointParametersRoundTrip() {
        final FloatBuffer attenuation = BufferUtils.createFloatBuffer(16);
        final float savedMin = GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN);
        final float savedMax = GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MAX);
        final float savedThreshold = GLSMQueries.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE);
        final int savedOrigin = GLSMQueries.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN);
        final int base = GLSMAttribStack.getAttribDepth();
        try {
            assertEquals(1.0f, savedMax);
            GLSMAttribStack.glPushAttrib(GL11.GL_POINT_BIT);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MIN, 2.0f);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_SIZE_MAX, 8.0f);
            GLSMRasterizer.glPointParameterf(GL14.GL_POINT_FADE_THRESHOLD_SIZE, 3.0f);
            GLSMRasterizer.glPointParameteri(GL20.GL_POINT_SPRITE_COORD_ORIGIN, GL20.GL_LOWER_LEFT);
            attenuation.clear();
            attenuation.put(0, 0.5f).put(1, 0.25f).put(2, 0.125f);
            GLSMRasterizer.glPointParameter(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            assertEquals(2.0f, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(8.0f, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MAX));
            assertEquals(3.0f, GLSMQueries.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertEquals(GL20.GL_LOWER_LEFT, GLSMQueries.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
            attenuation.clear();
            GLSMQueries.glGetFloat(GL14.GL_POINT_DISTANCE_ATTENUATION, attenuation);
            assertEquals(0.25f, attenuation.get(1));
            GLSMAttribStack.glPopAttrib();
            assertEquals(savedMin, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MIN));
            assertEquals(savedMax, GLSMQueries.glGetFloat(GL14.GL_POINT_SIZE_MAX));
            assertEquals(savedThreshold, GLSMQueries.glGetFloat(GL14.GL_POINT_FADE_THRESHOLD_SIZE));
            assertEquals(savedOrigin, GLSMQueries.glGetInteger(GL20.GL_POINT_SPRITE_COORD_ORIGIN));
        } finally {
            popAttribTo(base);
        }
    }
}
