package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_UnitTest {

    @Test
    void testDisplayListCompileCall() {
        verifyState(GL11.GL_LIGHTING, false, "GL_LIGHTING Initial State");

        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glEndList();

        verifyState(GL11.GL_LIGHTING, false, "GL_LIGHTING Post Display List Compile");

        GLStateManager.glCallList(list);

        verifyState(GL11.GL_LIGHTING, true, "GL_LIGHTING Post Display List Call");

        // Reset state that we changed during test
        GLStateManager.glDisable(GL11.GL_LIGHTING);
    }

    @Test
    void testDisplayListCompileExecute() {
        verifyState(GL11.GL_LIGHTING, false, "GL_LIGHTING Initial State");

        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glEndList();

        verifyState(GL11.GL_LIGHTING, true, "GL_LIGHTING Post Display List Compile and Execute");

        // Reset state that we changed during test
        GLStateManager.glDisable(GL11.GL_LIGHTING);
    }

    // ==================== CullFace Tests ====================

    @Test
    void testCullFaceInDisplayList() {
        // Get initial cull face mode
        int initialMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);

        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glCullFace(GL11.GL_FRONT);
        GLStateManager.glEndList();

        // After compile, mode should be unchanged
        assertEquals(initialMode, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "CullFace mode should be unchanged after GL_COMPILE");

        // Call the display list
        GLStateManager.glCallList(list);

        // Now mode should be GL_FRONT
        assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "CullFace mode should be GL_FRONT after calling display list");

        // Cleanup
        GLStateManager.glCullFace(GL11.GL_BACK);  // Reset to default
        GLStateManager.glDeleteLists(list, 1);
    }

    @Test
    void testCullFaceCompileAndExecute() {
        // Get initial cull face mode
        int initialMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);

        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glCullFace(GL11.GL_FRONT_AND_BACK);
        GLStateManager.glEndList();

        // After compile and execute, mode should be changed
        assertEquals(GL11.GL_FRONT_AND_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "CullFace mode should be GL_FRONT_AND_BACK after GL_COMPILE_AND_EXECUTE");

        // Cleanup
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glDeleteLists(list, 1);
    }

    // ==================== LogicOp Tests ====================

    @Test
    void testLogicOpInDisplayList() {
        // Get initial logic op
        int initialOp = GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE);

        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glLogicOp(GL11.GL_XOR);
        GLStateManager.glEndList();

        // After compile, op should be unchanged
        assertEquals(initialOp, GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE), "LogicOp should be unchanged after GL_COMPILE");

        // Call the display list
        GLStateManager.glCallList(list);

        // Now op should be GL_XOR
        assertEquals(GL11.GL_XOR, GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE), "LogicOp should be GL_XOR after calling display list");

        // Cleanup
        GLStateManager.glLogicOp(GL11.GL_COPY);  // Reset to default
        GLStateManager.glDeleteLists(list, 1);
    }

    @Test
    void testLogicOpCompileAndExecute() {
        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glLogicOp(GL11.GL_INVERT);
        GLStateManager.glEndList();

        // After compile and execute, op should be changed
        assertEquals(GL11.GL_INVERT, GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE), "LogicOp should be GL_INVERT after GL_COMPILE_AND_EXECUTE");

        // Cleanup
        GLStateManager.glLogicOp(GL11.GL_COPY);
        GLStateManager.glDeleteLists(list, 1);
    }

    @Test
    void testMultipleStateChangesInDisplayList() {
        // Test that multiple state commands work together
        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glCullFace(GL11.GL_FRONT);
        GLStateManager.glLogicOp(GL11.GL_AND);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glEndList();

        // Call the list
        GLStateManager.glCallList(list);

        // Verify all states changed
        assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "CullFace should be GL_FRONT");
        assertEquals(GL11.GL_AND, GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE), "LogicOp should be GL_AND");
        verifyState(GL11.GL_LIGHTING, true, "GL_LIGHTING should be enabled");

        // Cleanup
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glLogicOp(GL11.GL_COPY);
        GLStateManager.glDisable(GL11.GL_LIGHTING);
        GLStateManager.glDeleteLists(list, 1);
    }

    // ==================== ClearDepth Tests ====================

    @Test
    void testClearDepthInDisplayList() {
        // Get initial clear depth
        double initialDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);

        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glClearDepth(0.5);
        GLStateManager.glEndList();

        // After compile, depth should be unchanged
        assertEquals(initialDepth, GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE), 0.0001, "ClearDepth should be unchanged after GL_COMPILE");

        // Call the display list
        GLStateManager.glCallList(list);

        // Now depth should be 0.5
        assertEquals(0.5, GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE), 0.0001, "ClearDepth should be 0.5 after calling display list");

        // Cleanup
        GLStateManager.glClearDepth(1.0);  // Reset to default
        GLStateManager.glDeleteLists(list, 1);
    }

    @Test
    void testClearDepthCompileAndExecute() {
        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glClearDepth(0.25);
        GLStateManager.glEndList();

        // After compile and execute, depth should be changed
        assertEquals(0.25, GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE), 0.0001, "ClearDepth should be 0.25 after GL_COMPILE_AND_EXECUTE");

        // Cleanup
        GLStateManager.glClearDepth(1.0);
        GLStateManager.glDeleteLists(list, 1);
    }
}
