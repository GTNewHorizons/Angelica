package com.gtnewhorizons.angelica.api.glstate;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class GLState_GLTest {

    @BeforeEach
    void saveState() {
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
    }

    @AfterEach
    void restoreState() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPopMatrix();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPopMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPopAttrib();
    }

    @ParameterizedTest
    @ValueSource(ints = {GL11.GL_LINEAR, GL11.GL_EXP, GL11.GL_EXP2})
    void fogMirrorsGlsmState(int mode) {
        GLStateManager.glEnable(GL11.GL_FOG);
        GLStateManager.glFogi(GL11.GL_FOG_MODE, mode);
        GLStateManager.glFogf(GL11.GL_FOG_START, 12.5f);
        GLStateManager.glFogf(GL11.GL_FOG_END, 99.25f);
        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.375f);
        final FloatBuffer color = BufferUtils.createFloatBuffer(4);
        color.put(new float[] {0.25f, 0.5f, 0.75f, 0.125f}).flip();
        GLStateManager.glFog(GL11.GL_FOG_COLOR, color);

        assertTrue(GLState.isFogEnabled());
        assertEquals(mode, GLState.getFogMode());
        assertEquals(12.5f, GLState.getFogStart());
        assertEquals(99.25f, GLState.getFogEnd());
        assertEquals(0.375f, GLState.getFogDensity());

        final Vector4f rgba = GLState.getFogColor(new Vector4f());
        assertEquals(new Vector4f(0.25f, 0.5f, 0.75f, 0.125f), rgba);

        final FloatBuffer out = BufferUtils.createFloatBuffer(6);
        out.position(1);
        GLState.getFogColor(out);
        assertEquals(1, out.position());
        assertEquals(0.25f, out.get(1));
        assertEquals(0.5f, out.get(2));
        assertEquals(0.75f, out.get(3));
        assertEquals(0.125f, out.get(4));

        GLStateManager.glDisable(GL11.GL_FOG);
        assertFalse(GLState.isFogEnabled());
    }

    @Test
    void matricesAreCopies() {
        final Matrix4f modelView = new Matrix4f().translation(1, 2, 3).rotateX(0.5f);
        final Matrix4f projection = new Matrix4f().perspective(1.2f, 1.5f, 0.1f, 200f);
        loadMatrix(GL11.GL_MODELVIEW, modelView);
        loadMatrix(GL11.GL_PROJECTION, projection);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        final Matrix4f dest = new Matrix4f();
        assertSame(GLState.getModelViewMatrix(dest), dest);
        assertEquals(modelView, dest, "modelview");

        assertSame(GLState.getProjectionMatrix(dest), dest);
        assertEquals(projection, dest, "projection");

        dest.zero();
        assertEquals(modelView, GLState.getModelViewMatrix(new Matrix4f()));
    }

    private static void loadMatrix(int mode, Matrix4f m) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        m.get(buf);
        GLStateManager.glMatrixMode(mode);
        GLStateManager.glLoadMatrix(buf);
    }
}
