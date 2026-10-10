package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.immediateQuad;
import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.rawQuad;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class GLSM_DisplayList_RawTessellatorTransform_Test {

    private int testList = -1;

    @AfterEach
    void cleanup() {
        if (testList > 0) {
            GLStateManager.glDeleteLists(testList, 1);
            testList = -1;
        }
        GLStateManager.glLoadIdentity();
    }

    @Test
    void pendingTransformIsEmittedBeforeARawDraw() {
        testList = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glScalef(0.5f, 0.5f, 0.5f);
        rawQuad(1.0f);

        GLStateManager.glEndList();

        final CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled);

        final IntList opcodes = compiled.getCommandOpcodes();
        final int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        final int drawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(scaleIndex >= 0, "The pending scale must be recorded");
        assertTrue(drawIndex >= 0, "The raw draw must be recorded");
        assertTrue(scaleIndex < drawIndex, "Scale must be emitted before the draw it applies to");
        assertEquals(1, compiled.getCommandCounts().getOrDefault(GLCommand.SCALE, 0), "Scale emitted once, not again by the end-of-list flush");
    }

    @Test
    void pendingTransformIsNotEmittedBeforeABakedDraw() {
        testList = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glScalef(0.5f, 0.5f, 0.5f);
        immediateQuad(1.0f);

        GLStateManager.glEndList();

        final CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled);

        final IntList opcodes = compiled.getCommandOpcodes();
        final int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        final int drawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(drawIndex >= 0, "The immediate mode draw must be recorded");
        assertTrue(scaleIndex < 0 || drawIndex < scaleIndex, "Baked geometry must draw before the delta reaches the stream");
    }

    @Test
    void aRawDrawAfterABakedDrawStillGetsTheTransform() {
        testList = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glScalef(0.5f, 0.5f, 0.5f);
        immediateQuad(1.0f);
        rawQuad(1.0f);

        GLStateManager.glEndList();

        final CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled);

        final IntList opcodes = compiled.getCommandOpcodes();
        final int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        final int lastDrawIndex = opcodes.lastIndexOf(GLCommand.DRAW_RANGE);
        final int firstDrawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(scaleIndex >= 0, "The pending scale must be recorded");
        assertTrue(firstDrawIndex < scaleIndex, "The baked draw stays ahead of the transform");
        assertTrue(scaleIndex < lastDrawIndex, "The raw draw still gets the transform");
    }

    @Test
    void aRawDrawDoesNotFlushWhileBakedGeometryIsPending() {
        testList = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glScalef(0.5f, 0.5f, 0.5f);
        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glVertex3f(0.0f, 0.0f, 0.0f);
        GLStateManager.glVertex3f(1.0f, 0.0f, 0.0f);
        GLStateManager.glVertex3f(1.0f, 1.0f, 0.0f);
        GLStateManager.glVertex3f(0.0f, 1.0f, 0.0f);

        rawQuad(1.0f);

        GLStateManager.glEnd();
        GLStateManager.glEndList();

        final CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled);

        final IntList opcodes = compiled.getCommandOpcodes();
        final int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        final int drawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(drawIndex >= 0, "Draws must be recorded");
        assertTrue(scaleIndex < 0 || drawIndex < scaleIndex, "No flush while baked geometry is pending");
    }
}
