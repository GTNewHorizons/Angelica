package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.matrix;
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
}
