package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

/** Tests raw GL11 display list behavior (reference for GLSM). */
@ExtendWith(AngelicaExtension.class)
class DisplayListCompilationRawGLTest {

    private int testList = -1;

    private static void assertMatrixEquals(FloatBuffer expected, FloatBuffer actual, String message) {
        for (int i = 0; i < 16; i++) {
            assertEquals(expected.get(i), actual.get(i), 0.001f, message + " (element " + i + ")");
        }
    }

    @BeforeEach
    void setup() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @AfterEach
    void cleanup() {
        if (testList > 0) {
            GL11.glDeleteLists(testList, 1);
            testList = -1;
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    @Test
    void rawGL_compile_doesNotChangeState() {
        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE);
        GL11.glEnable(GL11.GL_BLEND);

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "During GL_COMPILE: state unchanged");

        GL11.glEndList();

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE: state unchanged");

        GL11.glCallList(testList);
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After playback: state changed");
    }

    @Test
    void rawGL_compileAndExecute_changesStateImmediately() {
        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);
        GL11.glEnable(GL11.GL_BLEND);

        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "During GL_COMPILE_AND_EXECUTE: state changed");

        GL11.glEndList();

        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE_AND_EXECUTE: state remains");
    }

    @Test
    void rawGL_compile_transformNotApplied() {
        FloatBuffer before = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, before);

        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE);
        GL11.glTranslatef(10, 20, 30);

        FloatBuffer during = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, during);
        assertMatrixEquals(before, during, "During GL_COMPILE: matrix unchanged");

        GL11.glEndList();
    }

    @Test
    void rawGL_compileAndExecute_transformAppliedImmediately() {
        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);
        GL11.glTranslatef(10, 0, 0);

        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        assertEquals(10.0f, matrix.get(12), 0.001f, "During GL_COMPILE_AND_EXECUTE: transform applied");

        GL11.glEndList();
    }

    @Test
    void rawGL_compile_multipleStateChanges() {
        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEndList();

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE: BLEND unchanged");
        assertFalse(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "After GL_COMPILE: DEPTH_TEST unchanged");
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE), "After GL_COMPILE: CULL_FACE unchanged");

        GL11.glCallList(testList);
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After playback: BLEND enabled");
        assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "After playback: DEPTH_TEST enabled");
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE), "After playback: CULL_FACE enabled");
    }

    @Test
    void rawGL_compile_matrixModeChangeRecorded() {
        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glTranslatef(5, 0, 0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glTranslatef(10, 0, 0);
        GL11.glEndList();

        FloatBuffer proj = BufferUtils.createFloatBuffer(16);
        FloatBuffer mv = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, proj);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        assertEquals(0.0f, proj.get(12), 0.001f, "After GL_COMPILE: PROJECTION unchanged");
        assertEquals(0.0f, mv.get(12), 0.001f, "After GL_COMPILE: MODELVIEW unchanged");

        GL11.glCallList(testList);

        proj.clear(); mv.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, proj);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        assertEquals(5.0f, proj.get(12), 0.001f, "After playback: PROJECTION has translate(5)");
        assertEquals(10.0f, mv.get(12), 0.001f, "After playback: MODELVIEW has translate(10)");
    }

    @Test
    void rawGL_compile_pushPopMatrixRecorded() {
        GL11.glTranslatef(100, 0, 0);  // Base transform

        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE);
        GL11.glPushMatrix();
        GL11.glTranslatef(10, 0, 0);
        GL11.glPopMatrix();
        GL11.glEndList();

        FloatBuffer afterCompile = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterCompile);
        assertEquals(100.0f, afterCompile.get(12), 0.001f, "After GL_COMPILE: matrix at base");

        GL11.glCallList(testList);

        FloatBuffer afterPlay = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterPlay);
        assertEquals(100.0f, afterPlay.get(12), 0.001f, "After playback: matrix at base (push/pop balanced)");
    }

    @Test
    void rawGL_compileAndExecute_pushPopMatrixExecutedImmediately() {
        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);

        GL11.glPushMatrix();
        GL11.glTranslatef(10, 0, 0);

        FloatBuffer inside = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, inside);
        assertEquals(10.0f, inside.get(12), 0.001f, "Inside push: translate visible");

        GL11.glPopMatrix();

        FloatBuffer afterPop = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterPop);
        assertEquals(0.0f, afterPop.get(12), 0.001f, "After pop: back to identity");

        GL11.glEndList();
    }

    // ==================== Immediate Mode with Transforms (Raw GL) ====================

    @Test
    void rawGL_compile_immediateModeWithTransforms() {
        // Verifies GL_COMPILE doesn't execute, playback applies transforms
        GL11.glLoadIdentity();
        GL11.glTranslatef(100.0f, 0.0f, 0.0f);  // Base position

        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE);

        GL11.glPushMatrix();
        GL11.glTranslatef(50.0f, 25.0f, 0.0f);
        GL11.glScalef(0.5f, 0.5f, 1.0f);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0.0f, 0.0f, 0.0f);
        GL11.glVertex3f(16.0f, 0.0f, 0.0f);
        GL11.glVertex3f(16.0f, 16.0f, 0.0f);
        GL11.glVertex3f(0.0f, 16.0f, 0.0f);
        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glEndList();

        // Matrix should still be at base position after GL_COMPILE
        FloatBuffer afterCompile = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterCompile);
        assertEquals(100.0f, afterCompile.get(12), 0.001f, "After GL_COMPILE: matrix unchanged");

        // Playback - push/pop should preserve base transform
        GL11.glCallList(testList);

        FloatBuffer afterPlayback = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterPlayback);
        assertEquals(100.0f, afterPlayback.get(12), 0.001f, "After playback: base position preserved");
        assertEquals(1.0f, afterPlayback.get(0), 0.001f, "After playback: no scale (popped)");

        GL11.glLoadIdentity();
    }

    @Test
    void rawGL_compileAndExecute_immediateModeExecutesImmediately() {
        // Verifies GL_COMPILE_AND_EXECUTE executes transforms during recording
        GL11.glLoadIdentity();

        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);

        GL11.glScalef(0.5f, 0.5f, 0.5f);

        FloatBuffer during = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, during);
        assertEquals(0.5f, during.get(0), 0.001f, "During COMPILE_AND_EXECUTE: scale applied");

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0.0f, 0.0f, 0.0f);
        GL11.glVertex3f(1.0f, 0.0f, 0.0f);
        GL11.glVertex3f(1.0f, 1.0f, 0.0f);
        GL11.glVertex3f(0.0f, 1.0f, 0.0f);
        GL11.glEnd();

        GL11.glEndList();

        FloatBuffer after = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, after);
        assertEquals(0.5f, after.get(0), 0.001f, "After COMPILE_AND_EXECUTE: scale remains");

        GL11.glLoadIdentity();
    }
}
