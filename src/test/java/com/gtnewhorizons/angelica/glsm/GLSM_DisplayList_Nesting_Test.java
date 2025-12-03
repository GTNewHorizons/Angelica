package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for nested display list execution.
 * Verifies that child display lists compose correctly with parent transforms.
 */
@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_Nesting_Test {

    private int parentList = -1;
    private int childList = -1;

    @AfterEach
    void cleanup() {
        if (parentList > 0) {
            GLStateManager.glDeleteLists(parentList, 1);
            parentList = -1;
        }
        if (childList > 0) {
            GLStateManager.glDeleteLists(childList, 1);
            childList = -1;
        }
        // Reset GL state
        GLStateManager.glLoadIdentity();
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Test
    void testNestedDisplayListWithParentTransform() {
        // Create child list: just a simple quad at origin
        childList = GL11.glGenLists(1);
        GLStateManager.glNewList(childList, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();
        GLStateManager.glEndList();

        // Create parent list: translate then call child
        parentList = GL11.glGenLists(1);
        GLStateManager.glNewList(parentList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);  // Parent transform
        GLStateManager.glCallList(childList);             // Should render child at (10,0,0)
        GLStateManager.glEndList();

        // Execute parent list
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(parentList);

        // Check final matrix state - should have parent's translate applied
        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Expected: translate(10, 0, 0)
        Matrix4f expected = new Matrix4f().translate(10.0f, 0.0f, 0.0f);
        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        // Compare buffers directly (both are column-major OpenGL format)
        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] mismatch", i));
        }

        GLStateManager.glPopMatrix();
    }

    @Test
    void testNestedDisplayListWithBothTransforms() {
        // Create child list: translate and draw
        childList = GL11.glGenLists(1);
        GLStateManager.glNewList(childList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);  // Child transform
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();
        GLStateManager.glEndList();

        // Create parent list: translate then call child
        parentList = GL11.glGenLists(1);
        GLStateManager.glNewList(parentList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);  // Parent transform
        GLStateManager.glCallList(childList);             // Child adds (1,0,0) to parent's (10,0,0)
        GLStateManager.glEndList();

        // Execute parent list
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(parentList);

        // Check final matrix state - should be translate(10,0,0) * translate(1,0,0) = translate(11,0,0)
        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Expected: translate(11, 0, 0) = parent(10) + child(1)
        Matrix4f expected = new Matrix4f().translate(11.0f, 0.0f, 0.0f);
        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        // Compare buffers directly
        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] mismatch", i));
        }

        GLStateManager.glPopMatrix();
    }

    @Test
    void testNestedDisplayListWithPushPop() {
        // Create child list with push/pop (shouldn't affect parent's matrix)
        childList = GL11.glGenLists(1);
        GLStateManager.glNewList(childList, GL11.GL_COMPILE);
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);  // Child transform
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();
        GLStateManager.glPopMatrix();  // Restore matrix to parent's state
        GLStateManager.glEndList();

        // Create parent list
        parentList = GL11.glGenLists(1);
        GLStateManager.glNewList(parentList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);  // Parent transform
        GLStateManager.glCallList(childList);             // Child uses push/pop
        GLStateManager.glEndList();

        // Execute parent list
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(parentList);

        // Check final matrix state - should still be translate(10,0,0) because child did push/pop
        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Expected: translate(10, 0, 0) only (child's transform was popped)
        Matrix4f expected = new Matrix4f().translate(10.0f, 0.0f, 0.0f);
        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        // Compare buffers directly
        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] mismatch", i));
        }

        GLStateManager.glPopMatrix();
    }

    @Test
    void testDeepNesting() {
        // Create deeply nested lists: grandchild -> child -> parent
        int grandchildList = GL11.glGenLists(1);
        GLStateManager.glNewList(grandchildList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);  // Grandchild: +1
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(1, 0, 0);
        GL11.glVertex3f(1, 1, 0);
        GL11.glVertex3f(0, 1, 0);
        GL11.glEnd();
        GLStateManager.glEndList();

        childList = GL11.glGenLists(1);
        GLStateManager.glNewList(childList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);  // Child: +10
        GLStateManager.glCallList(grandchildList);        // Grandchild: +1
        GLStateManager.glEndList();

        parentList = GL11.glGenLists(1);
        GLStateManager.glNewList(parentList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(100.0f, 0.0f, 0.0f);  // Parent: +100
        GLStateManager.glCallList(childList);              // Child: +10, Grandchild: +1
        GLStateManager.glEndList();

        // Execute parent list
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(parentList);

        // Check final matrix state - should be 100 + 10 + 1 = 111
        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Expected: translate(111, 0, 0)
        Matrix4f expected = new Matrix4f().translate(111.0f, 0.0f, 0.0f);
        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        // Compare buffers directly
        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] mismatch", i));
        }

        GLStateManager.glPopMatrix();

        // Cleanup grandchild
        GLStateManager.glDeleteLists(grandchildList, 1);
    }

    @Test
    void testMismatchedPushPopInSeparateLists() {
        // This test verifies that push and pop matrix operations can be split across separate display lists.
        // This is a uncommon but technically valid per the spec.

        // Get initial stack depth
        int initialDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);

        // Create a display list that only pushes the matrix
        int matrixPushList = GL11.glGenLists(1);
        GLStateManager.glNewList(matrixPushList, GL11.GL_COMPILE);
        GLStateManager.glPushMatrix();
        GLStateManager.glEndList();

        // Create a display list that only pops the matrix
        int matrixPopList = GL11.glGenLists(1);
        GLStateManager.glNewList(matrixPopList, GL11.GL_COMPILE);
        GLStateManager.glPopMatrix();
        GLStateManager.glEndList();

        // Verify initial stack depth hasn't changed after compilation
        assertEquals(initialDepth, GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH), "Stack depth should be unchanged after compiling display lists");

        // Call the push list - should increase stack depth
        GLStateManager.glCallList(matrixPushList);
        int depthAfterPush = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        assertEquals(initialDepth + 1, depthAfterPush, "Stack depth should increase by 1 after calling push list");

        // Optionally do some operations here that would benefit from the pushed matrix
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);

        // Call the pop list - should restore stack depth
        GLStateManager.glCallList(matrixPopList);
        int depthAfterPop = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        assertEquals(initialDepth, depthAfterPop, "Stack depth should return to initial after calling pop list");

        // Cleanup
        GLStateManager.glDeleteLists(matrixPushList, 1);
        GLStateManager.glDeleteLists(matrixPopList, 1);
    }
}
