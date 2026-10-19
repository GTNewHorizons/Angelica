package com.gtnewhorizons.angelica.glsm;

import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for nested display list execution.
 * Verifies that child display lists compose correctly with parent transforms.
 */
@GLCompatTest
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

        DisplayListTestHelper.assertModelviewAfterCall(parentList, new Matrix4f().translate(10.0f, 0.0f, 0.0f), "parent transform applies to the child");
    }

    static Stream<Arguments> nestedTranslationsCompose() {
        return Stream.of(
            Arguments.of(new float[] { 10.0f, 1.0f }, 11.0f),
            Arguments.of(new float[] { 100.0f, 10.0f, 1.0f }, 111.0f));
    }

    @ParameterizedTest(name = "{0} composes to {1}")
    @MethodSource
    void nestedTranslationsCompose(float[] chain, float expectedX) {
        final int[] lists = new int[chain.length];
        try {
            for (int depth = chain.length - 1; depth >= 0; depth--) {
                lists[depth] = GL11.glGenLists(1);
                GLStateManager.glNewList(lists[depth], GL11.GL_COMPILE);
                GLStateManager.glTranslatef(chain[depth], 0.0f, 0.0f);
                if (depth == chain.length - 1) {
                    GL11.glBegin(GL11.GL_QUADS);
                    GL11.glVertex3f(0, 0, 0);
                    GL11.glVertex3f(1, 0, 0);
                    GL11.glVertex3f(1, 1, 0);
                    GL11.glVertex3f(0, 1, 0);
                    GL11.glEnd();
                } else {
                    GLStateManager.glCallList(lists[depth + 1]);
                }
                GLStateManager.glEndList();
            }
            DisplayListTestHelper.assertModelviewAfterCall(lists[0], new Matrix4f().translate(expectedX, 0.0f, 0.0f), "nested translations");
        } finally {
            for (int list : lists) {
                if (list > 0) GLStateManager.glDeleteLists(list, 1);
            }
        }
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

        DisplayListTestHelper.assertModelviewAfterCall(parentList, new Matrix4f().translate(10.0f, 0.0f, 0.0f), "child push/pop leaves only the parent transform");
    }

    @Test
    void testMismatchedPushPopInSeparateLists() {
        // This test verifies that push and pop matrix operations can be split across separate display lists.
        // This is a uncommon but technically valid per the spec.

        // Get initial stack depth
        int initialDepth = GLStateManager.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);

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
        assertEquals(initialDepth, GLStateManager.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH), "Stack depth should be unchanged after compiling display lists");

        // Call the push list - should increase stack depth
        GLStateManager.glCallList(matrixPushList);
        int depthAfterPush = GLStateManager.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        assertEquals(initialDepth + 1, depthAfterPush, "Stack depth should increase by 1 after calling push list");

        // Optionally do some operations here that would benefit from the pushed matrix
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);

        // Call the pop list - should restore stack depth
        GLStateManager.glCallList(matrixPopList);
        int depthAfterPop = GLStateManager.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        assertEquals(initialDepth, depthAfterPop, "Stack depth should return to initial after calling pop list");

        // Cleanup
        GLStateManager.glDeleteLists(matrixPushList, 1);
        GLStateManager.glDeleteLists(matrixPopList, 1);
    }
}
