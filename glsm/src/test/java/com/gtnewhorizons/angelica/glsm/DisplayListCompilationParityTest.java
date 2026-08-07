package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCompatTest
class DisplayListCompilationParityTest {

    enum Caller {
        GLSM {
            void newList(int list, int mode) { GLStateManager.glNewList(list, mode); }
            void endList() { GLStateManager.glEndList(); }
            void callList(int list) { GLStateManager.glCallList(list); }
            void enable(int cap) { GLStateManager.glEnable(cap); }
            void translate(float x, float y, float z) { GLStateManager.glTranslatef(x, y, z); }
            void pushMatrix() { GLStateManager.glPushMatrix(); }
            void popMatrix() { GLStateManager.glPopMatrix(); }
            void matrixMode(int mode) { GLStateManager.glMatrixMode(mode); }
            void getFloat(int pname, FloatBuffer dst) { GLStateManager.glGetFloat(pname, dst); }
            void flushMatrix() { DisplayListManager.flushMatrix(); }
        },
        RAW {
            void newList(int list, int mode) { GL11.glNewList(list, mode); }
            void endList() { GL11.glEndList(); }
            void callList(int list) { GL11.glCallList(list); }
            void enable(int cap) { GL11.glEnable(cap); }
            void translate(float x, float y, float z) { GL11.glTranslatef(x, y, z); }
            void pushMatrix() { GL11.glPushMatrix(); }
            void popMatrix() { GL11.glPopMatrix(); }
            void matrixMode(int mode) { GL11.glMatrixMode(mode); }
            void getFloat(int pname, FloatBuffer dst) { GL11.glGetFloat(pname, dst); }
            void flushMatrix() {}
        };

        abstract void newList(int list, int mode);
        abstract void endList();
        abstract void callList(int list);
        abstract void enable(int cap);
        abstract void translate(float x, float y, float z);
        abstract void pushMatrix();
        abstract void popMatrix();
        abstract void matrixMode(int mode);
        abstract void getFloat(int pname, FloatBuffer dst);

        /** GL applies transforms as they are issued; GLSM defers to its own stack until flushed. */
        abstract void flushMatrix();
    }

    private int testList = -1;

    private static FloatBuffer matrix(Caller c, int pname) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(16);
        c.getFloat(pname, buf);
        return buf;
    }

    private static void assertMatrixEquals(FloatBuffer expected, FloatBuffer actual, String message) {
        for (int i = 0; i < 16; i++) {
            assertEquals(expected.get(i), actual.get(i), 0.001f, message + " (element " + i + ")");
        }
    }

    @BeforeEach
    void setup() {
        if (DisplayListManager.isRecording()) {
            try { GLStateManager.glEndList(); } catch (Exception ignored) {}
        }
        GLStateManager.disableBlend();
        GLStateManager.disableDepthTest();
        GLStateManager.disableCull();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
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

    @ParameterizedTest
    @EnumSource(Caller.class)
    void compile_doesNotChangeState(Caller c) {
        testList = GL11.glGenLists(1);
        c.newList(testList, GL11.GL_COMPILE);
        c.enable(GL11.GL_BLEND);

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "During GL_COMPILE: state unchanged");

        c.endList();
        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE: state unchanged");

        c.callList(testList);
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After playback: state changed");
    }

    @ParameterizedTest
    @EnumSource(Caller.class)
    void compileAndExecute_changesStateImmediately(Caller c) {
        testList = GL11.glGenLists(1);
        c.newList(testList, GL11.GL_COMPILE_AND_EXECUTE);
        c.enable(GL11.GL_BLEND);

        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "During GL_COMPILE_AND_EXECUTE: state changed");

        c.endList();
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE_AND_EXECUTE: state remains");
    }

    @ParameterizedTest
    @EnumSource(Caller.class)
    void compileAndExecute_transformAppliedImmediately(Caller c) {
        testList = GL11.glGenLists(1);
        c.newList(testList, GL11.GL_COMPILE_AND_EXECUTE);
        c.translate(10, 0, 0);
        c.flushMatrix();

        assertEquals(10.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "During GL_COMPILE_AND_EXECUTE: transform applied immediately");

        c.enable(GL11.GL_BLEND);
        assertEquals(10.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "After a state command: still applied");

        c.endList();
        assertEquals(10.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "After endList: still applied");
    }

    @ParameterizedTest
    @EnumSource(Caller.class)
    void compileAndExecute_pushPopMatrixExecutedImmediately(Caller c) {
        testList = GL11.glGenLists(1);
        c.newList(testList, GL11.GL_COMPILE_AND_EXECUTE);

        c.pushMatrix();
        c.translate(10, 0, 0);
        c.flushMatrix();

        assertEquals(10.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "Inside push: translate visible");

        c.popMatrix();

        assertEquals(0.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "After pop: back to the pushed state");

        c.endList();
    }

    @ParameterizedTest
    @EnumSource(Caller.class)
    void compile_transformNotApplied(Caller c) {
        final FloatBuffer before = matrix(c, GL11.GL_MODELVIEW_MATRIX);

        testList = GL11.glGenLists(1);
        c.newList(testList, GL11.GL_COMPILE);
        c.translate(10, 20, 30);

        assertMatrixEquals(before, matrix(c, GL11.GL_MODELVIEW_MATRIX), "During GL_COMPILE: matrix unchanged");

        c.endList();
    }

    @ParameterizedTest
    @EnumSource(Caller.class)
    void compile_multipleStateChanges(Caller c) {
        testList = GL11.glGenLists(1);
        c.newList(testList, GL11.GL_COMPILE);
        c.enable(GL11.GL_BLEND);
        c.enable(GL11.GL_DEPTH_TEST);
        c.enable(GL11.GL_CULL_FACE);
        c.endList();

        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "After GL_COMPILE: BLEND unchanged");
        assertFalse(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "After GL_COMPILE: DEPTH_TEST unchanged");
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE), "After GL_COMPILE: CULL_FACE unchanged");

        c.callList(testList);
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "After playback: BLEND enabled");
        assertTrue(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "After playback: DEPTH_TEST enabled");
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE), "After playback: CULL_FACE enabled");
    }

    @ParameterizedTest
    @EnumSource(Caller.class)
    void compile_matrixModeChangeRecorded(Caller c) {
        testList = GL11.glGenLists(1);
        c.newList(testList, GL11.GL_COMPILE);
        c.matrixMode(GL11.GL_PROJECTION);
        c.translate(5, 0, 0);
        c.matrixMode(GL11.GL_MODELVIEW);
        c.translate(10, 0, 0);
        c.endList();

        assertEquals(0.0f, matrix(c, GL11.GL_PROJECTION_MATRIX).get(12), 0.001f, "After GL_COMPILE: PROJECTION unchanged");
        assertEquals(0.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "After GL_COMPILE: MODELVIEW unchanged");

        c.callList(testList);

        assertEquals(5.0f, matrix(c, GL11.GL_PROJECTION_MATRIX).get(12), 0.001f, "After playback: PROJECTION has translate(5)");
        assertEquals(10.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "After playback: MODELVIEW has translate(10)");
    }

    @ParameterizedTest
    @EnumSource(Caller.class)
    void compile_pushPopMatrixRecorded(Caller c) {
        c.translate(100, 0, 0);  // Base transform

        testList = GL11.glGenLists(1);
        c.newList(testList, GL11.GL_COMPILE);
        c.pushMatrix();
        c.translate(10, 0, 0);
        c.popMatrix();
        c.endList();

        assertEquals(100.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "After GL_COMPILE: matrix at base");

        c.callList(testList);

        assertEquals(100.0f, matrix(c, GL11.GL_MODELVIEW_MATRIX).get(12), 0.001f, "After playback: matrix at base (push/pop balanced)");
    }
}
