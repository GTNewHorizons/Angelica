package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCompatTest
class DisplayListCompilationTest {

    private int testList = -1;

    @BeforeEach
    void setup() {
        if (DisplayListManager.isRecording()) {
            try { GLStateManager.glEndList(); } catch (Exception ignored) {}
        }
        GLStateManager.disableBlend();
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
    }

    @AfterEach
    void cleanup() {
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
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
    }

    private static FloatBuffer matrix(int pname) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(pname, buf);
        return buf;
    }

    @Test
    void glsm_compileAndExecute_matrixModeWithTransforms() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);

        GLStateManager.glTranslatef(10, 0, 0);
        GLStateManager.glRotatef(45, 0, 1, 0);
        DisplayListManager.flushMatrix();

        assertNotEquals(0.0f, matrix(GL11.GL_MODELVIEW_MATRIX).get(12), "After transforms: applied immediately");

        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        assertEquals(0.0f, matrix(GL11.GL_PROJECTION_MATRIX).get(12), 0.001f, "PROJECTION still identity");

        GLStateManager.glEndList();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void glsm_unsupportedOpInList_abortsCleanly() {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        assertTrue(DisplayListManager.isRecording(), "recording after glNewList");

        assertThrows(UnsupportedOperationException.class, () -> GLStateManager.glReadBuffer(GL11.GL_FRONT));

        assertFalse(DisplayListManager.isRecording(), "recording state must be reset after an in-list throw");
        assertEquals(DisplayListManager.RecordMode.NONE, DisplayListManager.getRecordMode());

        assertDoesNotThrow(() -> GLStateManager.glReadBuffer(GL11.GL_FRONT));
        assertDoesNotThrow(GLStateManager::glEndList);
        GLStateManager.glReadBuffer(GL11.GL_BACK);
    }

    @Test
    void glsm_abortIfLeaked_recoversOpenList() {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        assertTrue(DisplayListManager.isRecording());

        DisplayListManager.abortIfLeaked();

        assertFalse(DisplayListManager.isRecording(), "frame-begin backstop must clear a leaked open list");
    }

    @Test
    void rawGL_compile_immediateModeWithTransforms() {
        GL11.glLoadIdentity();
        GL11.glTranslatef(100.0f, 0.0f, 0.0f);

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

        final FloatBuffer afterCompile = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterCompile);
        assertEquals(100.0f, afterCompile.get(12), 0.001f, "After GL_COMPILE: matrix unchanged");

        GL11.glCallList(testList);

        final FloatBuffer afterPlayback = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterPlayback);
        assertEquals(100.0f, afterPlayback.get(12), 0.001f, "After playback: base position preserved");
        assertEquals(1.0f, afterPlayback.get(0), 0.001f, "After playback: no scale (popped)");

        GL11.glLoadIdentity();
    }

    @Test
    void rawGL_compileAndExecute_immediateModeExecutesImmediately() {
        GL11.glLoadIdentity();

        testList = GL11.glGenLists(1);
        GL11.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);

        GL11.glScalef(0.5f, 0.5f, 0.5f);

        final FloatBuffer during = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, during);
        assertEquals(0.5f, during.get(0), 0.001f, "During COMPILE_AND_EXECUTE: scale applied");

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0.0f, 0.0f, 0.0f);
        GL11.glVertex3f(1.0f, 0.0f, 0.0f);
        GL11.glVertex3f(1.0f, 1.0f, 0.0f);
        GL11.glVertex3f(0.0f, 1.0f, 0.0f);
        GL11.glEnd();

        GL11.glEndList();

        final FloatBuffer after = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, after);
        assertEquals(0.5f, after.get(0), 0.001f, "After COMPILE_AND_EXECUTE: scale remains");

        GL11.glLoadIdentity();
    }
}
