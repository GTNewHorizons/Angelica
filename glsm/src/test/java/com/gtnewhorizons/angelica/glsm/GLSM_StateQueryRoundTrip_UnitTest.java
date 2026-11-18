package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCompatTest
public class GLSM_StateQueryRoundTrip_UnitTest {

    private static IntBuffer driverInts(int pname) {
        final IntBuffer buf = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(pname, buf);
        return buf;
    }

    @Test
    void activeTextureRoundTrips() {
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE3);
            assertEquals(GL13.GL_TEXTURE3, GLSMQueries.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "must return the GL_TEXTURE# enum, not the unit index");
            assertEquals(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GLSMQueries.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void invalidTextureUnitIsIgnored() {
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);

            GLSMTextures.glActiveTexture(0);
            assertEquals(GL13.GL_TEXTURE2, GLSMQueries.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
            assertEquals(GL13.GL_TEXTURE2, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));

            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_UNITS);
            assertEquals(GL13.GL_TEXTURE2, GLSMQueries.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
            assertEquals(GL13.GL_TEXTURE2, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        }
    }

    @Test
    void depthWriteMaskRoundTripsAsInteger() {
        try {
            GLSMDepthStencilAlpha.glDepthMask(false);
            assertEquals(GL11.GL_FALSE, GLSMQueries.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
            assertEquals(GL11.GL_FALSE, GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
            GLSMDepthStencilAlpha.glDepthMask(true);
            assertEquals(GL11.GL_TRUE, GLSMQueries.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
            assertEquals(GL11.GL_TRUE, GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
        } finally {
            GLSMDepthStencilAlpha.glDepthMask(true);
        }
    }

    @Test
    void cullFaceModeRoundTrips() {
        try {
            GLSMRasterizer.glCullFace(GL11.GL_FRONT);
            assertEquals(GL11.GL_FRONT, GLSMQueries.glGetInteger(GL11.GL_CULL_FACE_MODE));
            assertEquals(GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), GLSMQueries.glGetInteger(GL11.GL_CULL_FACE_MODE));
        } finally {
            GLSMRasterizer.glCullFace(GL11.GL_BACK);
        }
    }

    @Test
    void polygonModeRoundTripsBothFaces() {
        try {
            GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            assertEquals(GL11.GL_LINE, GLSMQueries.glGetInteger(GL11.GL_POLYGON_MODE));

            final IntBuffer params = BufferUtils.createIntBuffer(2);
            GLSMQueries.glGetInteger(GL11.GL_POLYGON_MODE, params);
            assertEquals(GL11.GL_LINE, params.get(0), "front mode");
            assertEquals(GL11.GL_LINE, params.get(1), "back mode");

            final IntBuffer driver = driverInts(GL11.GL_POLYGON_MODE);
            assertEquals(GL11.GL_LINE, driver.get(0), "driver front mode");
            assertEquals(GL11.GL_LINE, driver.get(1), "driver back mode");
        } finally {
            GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }
    }

    @Test
    void pixelUnpackStateRoundTrips() {
        try {
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 64);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 2);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 3);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

            assertEquals(64, GLSMQueries.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH));
            assertEquals(2, GLSMQueries.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS));
            assertEquals(3, GLSMQueries.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));
            assertEquals(1, GLSMQueries.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));

            assertEquals(64, GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH));
            assertEquals(2, GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS));
            assertEquals(3, GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));
            assertEquals(1, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
        } finally {
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        }
    }

    @Test
    void viewportFillsAllFourComponentsViaIntArray() {
        GLSMRasterizer.glViewport(3, 5, 17, 23);

        final int[] params = new int[4];
        GLSMQueries.glGetInteger(GL11.GL_VIEWPORT, params);
        assertEquals(3, params[0]);
        assertEquals(5, params[1]);
        assertEquals(17, params[2]);
        assertEquals(23, params[3]);

        final IntBuffer driver = driverInts(GL11.GL_VIEWPORT);
        assertEquals(3, driver.get(0));
        assertEquals(5, driver.get(1));
        assertEquals(17, driver.get(2));
        assertEquals(23, driver.get(3));
    }

    @Test
    void saveDisturbRestoreIsIdentity() {
        final int activeTexture = GLSMQueries.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int depthWriteMask = GLSMQueries.glGetInteger(GL11.GL_DEPTH_WRITEMASK);
        final int cullMode = GLSMQueries.glGetInteger(GL11.GL_CULL_FACE_MODE);
        final int polygonMode = GLSMQueries.glGetInteger(GL11.GL_POLYGON_MODE);
        final int unpackAlignment = GLSMQueries.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        final int[] viewport = new int[4];
        GLSMQueries.glGetInteger(GL11.GL_VIEWPORT, viewport);
        final int driverActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int driverDepthWriteMask = GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK);
        final int driverCullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        final int driverPolygonMode = GL11.glGetInteger(GL11.GL_POLYGON_MODE);
        final int driverUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        final IntBuffer driverViewport = driverInts(GL11.GL_VIEWPORT);

        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + 4);
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMRasterizer.glCullFace(GL11.GL_FRONT_AND_BACK);
        GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_POINT);
        GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 8);
        GLSMRasterizer.glViewport(1, 1, 2, 2);

        GLSMTextures.glActiveTexture(activeTexture);
        GLSMDepthStencilAlpha.glDepthMask(depthWriteMask == GL11.GL_TRUE);
        GLSMRasterizer.glCullFace(cullMode);
        GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonMode);
        GLSMTextures.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
        GLSMRasterizer.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        assertEquals(activeTexture, GLSMQueries.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(depthWriteMask, GLSMQueries.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
        assertEquals(cullMode, GLSMQueries.glGetInteger(GL11.GL_CULL_FACE_MODE));
        assertEquals(polygonMode, GLSMQueries.glGetInteger(GL11.GL_POLYGON_MODE));
        assertEquals(unpackAlignment, GLSMQueries.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
        final int[] restored = new int[4];
        GLSMQueries.glGetInteger(GL11.GL_VIEWPORT, restored);
        assertEquals(viewport[0], restored[0]);
        assertEquals(viewport[1], restored[1]);
        assertEquals(viewport[2], restored[2]);
        assertEquals(viewport[3], restored[3]);

        assertEquals(driverActiveTexture, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(driverDepthWriteMask, GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK));
        assertEquals(driverCullMode, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE));
        assertEquals(driverPolygonMode, GL11.glGetInteger(GL11.GL_POLYGON_MODE));
        assertEquals(driverUnpackAlignment, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT));
        final IntBuffer driverRestored = driverInts(GL11.GL_VIEWPORT);
        for (int i = 0; i < 4; i++) {
            assertEquals(driverViewport.get(i), driverRestored.get(i));
        }
    }

    @Test
    void uncachedTexParameterFallsThroughToTheDriver() {
        final int tex = GLSMTextures.glGenTextures();
        try {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex);

            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            assertEquals(GL12.GL_CLAMP_TO_EDGE, GLSMTextures.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_R), "uncached pname must fall through to the driver");
            assertEquals(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_R),
                GLSMTextures.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_R), "uncached pname must match the driver");
        } finally {
            GLSMTextures.glDeleteTextures(tex);
        }
    }
}
