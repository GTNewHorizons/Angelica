package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCompatTest
public class GLSM_StateQueryRoundTrip_UnitTest {

    @Test
    void activeTextureRoundTrips() {
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE3);
            assertEquals(GL13.GL_TEXTURE3, GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "must return the GL_TEXTURE# enum, not the unit index");
            assertEquals(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void invalidTextureUnitIsIgnored() {
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);

            GLStateManager.glActiveTexture(0);
            assertEquals(GL13.GL_TEXTURE2, GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE));

            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_UNITS);
            assertEquals(GL13.GL_TEXTURE2, GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void depthWriteMaskRoundTripsAsInteger() {
        try {
            GLStateManager.glDepthMask(false);
            assertEquals(GL11.GL_FALSE, GLStateManager.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
            GLStateManager.glDepthMask(true);
            assertEquals(GL11.GL_TRUE, GLStateManager.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
        } finally {
            GLStateManager.glDepthMask(true);
        }
    }

    @Test
    void cullFaceModeRoundTrips() {
        try {
            GLStateManager.glCullFace(GL11.GL_FRONT);
            assertEquals(GL11.GL_FRONT, GLStateManager.glGetInteger(GL11.GL_CULL_FACE_MODE));
            assertEquals(GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), GLStateManager.glGetInteger(GL11.GL_CULL_FACE_MODE));
        } finally {
            GLStateManager.glCullFace(GL11.GL_BACK);
        }
    }

    @Test
    void polygonModeRoundTripsBothFaces() {
        try {
            GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            assertEquals(GL11.GL_LINE, GLStateManager.glGetInteger(GL11.GL_POLYGON_MODE));

            final IntBuffer params = BufferUtils.createIntBuffer(2);
            GLStateManager.glGetInteger(GL11.GL_POLYGON_MODE, params);
            assertEquals(GL11.GL_LINE, params.get(0), "front mode");
            assertEquals(GL11.GL_LINE, params.get(1), "back mode");
        } finally {
            GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }
    }

    @Test
    void pixelUnpackStateRoundTrips() {
        try {
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 64);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 2);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 3);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

            assertEquals(64, GLStateManager.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH));
            assertEquals(2, GLStateManager.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS));
            assertEquals(3, GLStateManager.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));
            assertEquals(1, GLStateManager.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
        } finally {
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        }
    }

    @Test
    void viewportFillsAllFourComponentsViaIntArray() {
        GLStateManager.glViewport(3, 5, 17, 23);

        final int[] params = new int[4];
        GLStateManager.glGetInteger(GL11.GL_VIEWPORT, params);
        assertEquals(3, params[0]);
        assertEquals(5, params[1]);
        assertEquals(17, params[2]);
        assertEquals(23, params[3]);
    }

    @Test
    void saveDisturbRestoreIsIdentity() {
        final int activeTexture = GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int depthWriteMask = GLStateManager.glGetInteger(GL11.GL_DEPTH_WRITEMASK);
        final int cullMode = GLStateManager.glGetInteger(GL11.GL_CULL_FACE_MODE);
        final int polygonMode = GLStateManager.glGetInteger(GL11.GL_POLYGON_MODE);
        final int unpackAlignment = GLStateManager.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        final int[] viewport = new int[4];
        GLStateManager.glGetInteger(GL11.GL_VIEWPORT, viewport);

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + 4);
        GLStateManager.glDepthMask(false);
        GLStateManager.glCullFace(GL11.GL_FRONT_AND_BACK);
        GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_POINT);
        GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 8);
        GLStateManager.glViewport(1, 1, 2, 2);

        GLStateManager.glActiveTexture(activeTexture);
        GLStateManager.glDepthMask(depthWriteMask == GL11.GL_TRUE);
        GLStateManager.glCullFace(cullMode);
        GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonMode);
        GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
        GLStateManager.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        assertEquals(activeTexture, GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(depthWriteMask, GLStateManager.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
        assertEquals(cullMode, GLStateManager.glGetInteger(GL11.GL_CULL_FACE_MODE));
        assertEquals(polygonMode, GLStateManager.glGetInteger(GL11.GL_POLYGON_MODE));
        assertEquals(unpackAlignment, GLStateManager.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
        final int[] restored = new int[4];
        GLStateManager.glGetInteger(GL11.GL_VIEWPORT, restored);
        assertEquals(viewport[0], restored[0]);
        assertEquals(viewport[1], restored[1]);
        assertEquals(viewport[2], restored[2]);
        assertEquals(viewport[3], restored[3]);
    }
}
