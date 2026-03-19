package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests GLSM display list behavior matches OpenGL spec. */
@ExtendWith(GLSMExtension.class)
class DisplayListCompilationGLSMTest {

    private int testList = -1;

    private static void assertMatrixEquals(FloatBuffer expected, FloatBuffer actual, String message) {
        for (int i = 0; i < 16; i++) {
            assertEquals(expected.get(i), actual.get(i), 0.001f, message + " (element " + i + ")");
        }
    }

    @BeforeEach
    void setup() {
        // Ensure not recording from a previous failed test
        if (DisplayListManager.isRecording()) {
            try { GLStateManager.glEndList(); } catch (Exception ignored) {}
        }
        GLStateManager.disableBlend();
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();
        // Reset matrix to identity for transform tests
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
    }

    @AfterEach
    void cleanup() {
        // End any open recording
        if (DisplayListManager.isRecording()) {
            try { GLStateManager.glEndList(); } catch (Exception ignored) {}
        }
        if (testList > 0) {
            GLStateManager.glDeleteLists(testList, 1);
            testList = -1;
        }
        GLStateManager.disableBlend();
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();
    }

    @Test
    void glsm_compile_doesNotChangeState() {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.enableBlend();

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "During GL_COMPILE: state unchanged");

        GLStateManager.glEndList();

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE: state unchanged");

        GLStateManager.glCallList(testList);
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After playback: state changed");
    }

    @Test
    void glsm_compileAndExecute_changesStateImmediately() {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.enableBlend();

        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "During GL_COMPILE_AND_EXECUTE: state changed");

        GLStateManager.glEndList();

        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE_AND_EXECUTE: state remains");
    }

    @Test
    void glsm_compile_transformNotApplied() {
        FloatBuffer before = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, before);

        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(10, 20, 30);

        FloatBuffer during = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, during);
        assertMatrixEquals(before, during, "During GL_COMPILE: matrix unchanged");

        GLStateManager.glEndList();
    }

    @Test
    void glsm_compileAndExecute_transformAppliedImmediately() {
        // COMPILE_AND_EXECUTE: transforms apply to GLSM stack immediately (not deferred). This is required because state commands between transforms and draws
        // may trigger Iris program activation, which reads the matrix from the GLSM stack.
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glTranslatef(10, 0, 0);  // Applied immediately

        FloatBuffer afterTranslate = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterTranslate);
        assertEquals(10.0f, afterTranslate.get(12), 0.001f, "After translate: applied immediately");

        GLStateManager.enableBlend();

        FloatBuffer afterStateCmd = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterStateCmd);
        assertEquals(10.0f, afterStateCmd.get(12), 0.001f, "After state cmd: still applied");

        GLStateManager.glEndList();

        FloatBuffer afterEndList = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterEndList);
        assertEquals(10.0f, afterEndList.get(12), 0.001f, "After glEndList: still applied");
    }

    @Test
    void glsm_playback_appliesRecordedState() {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.enableBlend();
        GLStateManager.glEndList();

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "After compile: unchanged");

        GLStateManager.glCallList(testList);
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After playback: enabled");
    }

    @Test
    void glsm_compile_multipleStateChanges() {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.enableBlend();
        GLStateManager.enableDepthTest();
        GLStateManager.enableCull();
        GLStateManager.glEndList();

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE: BLEND unchanged");
        assertFalse(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "After GL_COMPILE: DEPTH_TEST unchanged");
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE), "After GL_COMPILE: CULL_FACE unchanged");

        GLStateManager.glCallList(testList);
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After playback: BLEND enabled");
        assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "After playback: DEPTH_TEST enabled");
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE), "After playback: CULL_FACE enabled");
    }

    @Test
    void glsm_compile_matrixModeChangeRecorded() {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glTranslatef(5, 0, 0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glTranslatef(10, 0, 0);
        GLStateManager.glEndList();

        FloatBuffer proj = BufferUtils.createFloatBuffer(16);
        FloatBuffer mv = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_PROJECTION_MATRIX, proj);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        assertEquals(0.0f, proj.get(12), 0.001f, "After GL_COMPILE: PROJECTION unchanged");
        assertEquals(0.0f, mv.get(12), 0.001f, "After GL_COMPILE: MODELVIEW unchanged");

        GLStateManager.glCallList(testList);

        proj.clear(); mv.clear();
        GLStateManager.glGetFloat(GL11.GL_PROJECTION_MATRIX, proj);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        assertEquals(5.0f, proj.get(12), 0.001f, "After playback: PROJECTION has translate(5)");
        assertEquals(10.0f, mv.get(12), 0.001f, "After playback: MODELVIEW has translate(10)");
    }

    @Test
    void glsm_compile_pushPopMatrixRecorded() {
        GLStateManager.glTranslatef(100, 0, 0);  // Base transform

        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(10, 0, 0);
        GLStateManager.glPopMatrix();
        GLStateManager.glEndList();

        FloatBuffer afterCompile = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterCompile);
        assertEquals(100.0f, afterCompile.get(12), 0.001f, "After GL_COMPILE: matrix at base");

        GLStateManager.glCallList(testList);

        FloatBuffer afterPlay = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterPlay);
        assertEquals(100.0f, afterPlay.get(12), 0.001f, "After playback: matrix at base");
    }

    @Test
    void glsm_compileAndExecute_matrixModeWithTransforms() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);

        GLStateManager.glTranslatef(10, 0, 0);  // Applied immediately
        GLStateManager.glRotatef(45, 0, 1, 0);  // Applied immediately

        FloatBuffer afterTransforms = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterTransforms);
        assertNotEquals(0.0f, afterTransforms.get(12), "After transforms: applied immediately");

        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);

        FloatBuffer proj = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_PROJECTION_MATRIX, proj);
        assertEquals(0.0f, proj.get(12), 0.001f, "PROJECTION still identity");

        GLStateManager.glEndList();

        // Cleanup: restore matrix mode to MODELVIEW
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void glsm_compileAndExecute_pushPopMatrix() {
        // Push/pop work correctly with immediate transform application
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);

        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(10, 0, 0);  // Applied immediately

        FloatBuffer afterTranslate = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterTranslate);
        assertEquals(10.0f, afterTranslate.get(12), 0.001f, "After translate: applied immediately");

        GLStateManager.glPopMatrix();  // Pops back to pre-push state

        FloatBuffer afterPop = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterPop);
        assertEquals(0.0f, afterPop.get(12), 0.001f, "After pop: back to identity (pushed state)");

        GLStateManager.glEndList();
    }
}
