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
            try { GLSMDraws.glEndList(); } catch (Exception ignored) {}
        }
        GLSMBlend.disableBlend();
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMRasterizer.disableCull();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
    }

    @AfterEach
    void cleanup() {
        if (DisplayListManager.isRecording()) {
            try { GLSMDraws.glEndList(); } catch (Exception ignored) {}
        }
        if (testList > 0) {
            GLSMDraws.glDeleteLists(testList, 1);
            testList = -1;
        }
        GLSMBlend.disableBlend();
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMRasterizer.disableCull();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
    }

    @Test
    void glsm_compileAndExecute_matrixModeWithTransforms() {
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);

        testList = GL11.glGenLists(1);
        GLSMDraws.glNewList(testList, GL11.GL_COMPILE_AND_EXECUTE);

        GLSMMatrices.glTranslatef(10, 0, 0);
        GLSMMatrices.glRotatef(45, 0, 1, 0);
        DisplayListManager.flushMatrix();

        assertNotEquals(0.0f, matrix(GL11.GL_MODELVIEW_MATRIX).get(12), "After transforms: applied immediately");

        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        assertEquals(0.0f, matrix(GL11.GL_PROJECTION_MATRIX).get(12), 0.001f, "PROJECTION still identity");

        GLSMDraws.glEndList();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void glsm_unsupportedOpInList_abortsCleanly() {
        testList = GL11.glGenLists(1);
        GLSMDraws.glNewList(testList, GL11.GL_COMPILE);
        assertTrue(DisplayListManager.isRecording(), "recording after glNewList");

        assertThrows(UnsupportedOperationException.class, () -> GLSMFramebuffer.glReadBuffer(GL11.GL_FRONT));

        assertFalse(DisplayListManager.isRecording(), "recording state must be reset after an in-list throw");
        assertEquals(DisplayListManager.RecordMode.NONE, DisplayListManager.getRecordMode());

        assertDoesNotThrow(() -> GLSMFramebuffer.glReadBuffer(GL11.GL_FRONT));
        assertDoesNotThrow(GLSMDraws::glEndList);
        GLSMFramebuffer.glReadBuffer(GL11.GL_BACK);
    }

    @Test
    void glsm_abortIfLeaked_recoversOpenList() {
        testList = GL11.glGenLists(1);
        GLSMDraws.glNewList(testList, GL11.GL_COMPILE);
        assertTrue(DisplayListManager.isRecording());

        DisplayListManager.abortIfLeaked();

        assertFalse(DisplayListManager.isRecording(), "frame-begin backstop must clear a leaked open list");
    }
}
