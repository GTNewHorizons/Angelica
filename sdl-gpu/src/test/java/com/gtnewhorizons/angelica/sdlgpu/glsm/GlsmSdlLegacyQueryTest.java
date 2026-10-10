package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlLegacyQueryTest {

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void clientActiveTextureRoundTrips() {
        try {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            final int saved = GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
            assertEquals(GL13.GL_TEXTURE1, saved);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glClientActiveTexture(saved);
            assertEquals(1, GLStateManager.getClientActiveTextureUnit());
            GLStateManager.glClientActiveTexture(0);
            assertEquals(1, GLStateManager.getClientActiveTextureUnit());
            assertEquals(GLStateManager.MAX_TEXTURE_COORDS, GLStateManager.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS));
        } finally {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void clientArrayEnablesRoundTrip() {
        final int[] caps = { GL11.GL_VERTEX_ARRAY, GL11.GL_NORMAL_ARRAY, GL11.GL_COLOR_ARRAY, GL11.GL_TEXTURE_COORD_ARRAY, GL11.GL_INDEX_ARRAY, GL11.GL_EDGE_FLAG_ARRAY, GL14.GL_SECONDARY_COLOR_ARRAY, GL14.GL_FOG_COORDINATE_ARRAY };
        for (final int cap : caps) {
            final boolean was = GLStateManager.glIsEnabled(cap);
            try {
                GLStateManager.glEnableClientState(cap);
                assertTrue(GLStateManager.glIsEnabled(cap), "enabled 0x" + Integer.toHexString(cap));
                assertEquals(1, GLStateManager.glGetInteger(cap), "enabled int 0x" + Integer.toHexString(cap));
                GLStateManager.glDisableClientState(cap);
                assertFalse(GLStateManager.glGetBoolean(cap), "disabled 0x" + Integer.toHexString(cap));
            } finally {
                if (was) {
                    GLStateManager.glEnableClientState(cap);
                } else {
                    GLStateManager.glDisableClientState(cap);
                }
            }
        }
    }

    @Test
    void hintsRoundTripAndFollowHintBit() {
        final int[] targets = { GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_POINT_SMOOTH_HINT, GL11.GL_LINE_SMOOTH_HINT, GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_FOG_HINT, GL13.GL_TEXTURE_COMPRESSION_HINT, GL14.GL_GENERATE_MIPMAP_HINT, GL20.GL_FRAGMENT_SHADER_DERIVATIVE_HINT };
        for (final int target : targets) {
            final int saved = GLStateManager.glGetInteger(target);
            try {
                GLStateManager.glHint(target, GL11.GL_NICEST);
                assertEquals(GL11.GL_NICEST, GLStateManager.glGetInteger(target), "set 0x" + Integer.toHexString(target));
                assertEquals((float) GL11.GL_NICEST, GLStateManager.glGetFloat(target), "float 0x" + Integer.toHexString(target));
                GLStateManager.glPushAttrib(GL11.GL_HINT_BIT);
                GLStateManager.glHint(target, GL11.GL_FASTEST);
                assertEquals(GL11.GL_FASTEST, GLStateManager.glGetInteger(target), "inside push 0x" + Integer.toHexString(target));
                GLStateManager.glPopAttrib();
                assertEquals(GL11.GL_NICEST, GLStateManager.glGetInteger(target), "after pop 0x" + Integer.toHexString(target));
            } finally {
                GLStateManager.glHint(target, saved);
            }
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
            while (GLStateManager.getAttribDepth() > base) {
                GLStateManager.glPopAttrib();
            }
        }
    }

    @Test
    void cacheOnlyEnablesRoundTrip() {
        final int[] caps = { GL20.GL_POINT_SPRITE, GL20.GL_VERTEX_PROGRAM_TWO_SIDE, GL13.GL_TEXTURE_CUBE_MAP };
        for (final int cap : caps) {
            final boolean was = GLStateManager.glIsEnabled(cap);
            try {
                GLStateManager.glEnable(cap);
                assertTrue(GLStateManager.glIsEnabled(cap), "enabled 0x" + Integer.toHexString(cap));
                assertEquals(1, GLStateManager.glGetInteger(cap), "enabled int 0x" + Integer.toHexString(cap));
                GLStateManager.glDisable(cap);
                assertFalse(GLStateManager.glGetBoolean(cap), "disabled 0x" + Integer.toHexString(cap));
            } finally {
                if (was) {
                    GLStateManager.glEnable(cap);
                } else {
                    GLStateManager.glDisable(cap);
                }
            }
        }
        final boolean edge = GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG);
        try {
            GLStateManager.glEdgeFlag(false);
            assertFalse(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG));
            GLStateManager.glEdgeFlag(true);
            assertTrue(GLStateManager.glGetBoolean(GL11.GL_EDGE_FLAG));
        } finally {
            GLStateManager.glEdgeFlag(edge);
        }
    }

    @Test
    void clientArrayPointersRoundTripThroughQueriesAndClientAttribStack() {
        final FloatBuffer positions = BufferUtils.createFloatBuffer(12);
        final FloatBuffer other = BufferUtils.createFloatBuffer(12);
        try {
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

            assertEquals(3, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE));
            assertEquals(12, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE));
            assertEquals(4, GLStateManager.glGetInteger(GL14.GL_FOG_COORDINATE_ARRAY_STRIDE));
            assertFalse(GLStateManager.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY));
        } finally {
            GLStateManager.glDisableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
        }
    }

    @Test
    void crossTypeReadsConvert() {
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_CURRENT_COLOR, savedColor);
        final boolean savedLighting = GLStateManager.glIsEnabled(GL11.GL_LIGHTING);
        try {
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
        } finally {
            GLStateManager.glColor4f(savedColor.get(0), savedColor.get(1), savedColor.get(2), savedColor.get(3));
            if (savedLighting) {
                GLStateManager.glEnable(GL11.GL_LIGHTING);
            } else {
                GLStateManager.glDisable(GL11.GL_LIGHTING);
            }
        }
    }
}
