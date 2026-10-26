package com.gtnewhorizons.angelica.glsm;

import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.assertModelviewAfterCall;
import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.modelviewAfterCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stress tests for display list system.
 * Verifies system stability under high load conditions.
 *
 * <p>Note: Tests that require actual geometry capture via Tessellator
 * need the full Minecraft environment. These tests focus on matrix
 * operations and display list lifecycle which work with raw GL calls.</p>
 */
@GLCompatTest
class GLSM_DisplayList_Stress_Test {

    private final List<Integer> testLists = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (int list : testLists) {
            if (list > 0) {
                GLSMDraws.glDeleteLists(list, 1);
            }
        }
        testLists.clear();
        GLSMMatrices.glLoadIdentity();
    }

    private int createAndTrackList() {
        int list = GL11.glGenLists(1);
        testLists.add(list);
        return list;
    }

    @Test
    void testManyDisplayListsWithTransforms() {
        // Create and compile many display lists with transforms
        final int numLists = 500;

        for (int i = 0; i < numLists; i++) {
            int list = createAndTrackList();
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);

            // Each list has a unique transform
            GLSMMatrices.glTranslatef(i * 0.1f, 0, 0);

            GLSMDraws.glEndList();

            // Verify each list was compiled
            assertTrue(DisplayListManager.displayListExists(list),
                "Display list " + list + " should exist after compilation");
        }

        // Verify all lists still exist
        for (int list : testLists) {
            assertTrue(DisplayListManager.displayListExists(list),
                "All " + numLists + " display lists should exist");
        }
    }

    @Test
    void testManyTransformsInSingleList() {
        // Create a single display list with many transform operations
        final int numTransforms = 1000;

        int list = createAndTrackList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);

        // Many push/pop pairs with transforms
        for (int i = 0; i < numTransforms; i++) {
            GLSMMatrices.glPushMatrix();
            GLSMMatrices.glTranslatef(i * 0.001f, 0, 0);
            GLSMMatrices.glRotatef(i * 0.36f, 0, 1, 0);
            GLSMMatrices.glPopMatrix();
        }

        // Final transform that should be the result
        GLSMMatrices.glTranslatef(42.0f, 0, 0);

        GLSMDraws.glEndList();

        assertEquals(42.0f, modelviewAfterCall(list).get(12), 0.0001f, "After many push/pop pairs, only final transform should remain");
    }

    @Test
    void testRapidCompileDeleteCycle() {
        // Rapidly create and delete display lists to test resource management
        final int cycles = 100;

        for (int cycle = 0; cycle < cycles; cycle++) {
            int list = GL11.glGenLists(1);

            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMMatrices.glTranslatef(cycle, 0, 0);
            GLSMDraws.glEndList();

            // Immediately delete
            GLSMDraws.glDeleteLists(list, 1);

            // Verify it's gone
            assertFalse(DisplayListManager.displayListExists(list),
                "Display list should be deleted after glDeleteLists");
        }
    }

    @Test
    void testDeepNestingChain() {
        // Create a chain of display lists that call each other
        final int chainLength = 50;
        int[] chain = new int[chainLength];

        // Create from innermost to outermost
        for (int i = 0; i < chainLength; i++) {
            chain[i] = createAndTrackList();
            GLSMDraws.glNewList(chain[i], GL11.GL_COMPILE);

            // Each list translates by 1 and calls the previous list (if any)
            GLSMMatrices.glTranslatef(1.0f, 0.0f, 0.0f);

            if (i > 0) {
                GLSMDraws.glCallList(chain[i - 1]);
            }

            GLSMDraws.glEndList();
        }

        assertModelviewAfterCall(chain[chainLength - 1], new Matrix4f().translate(chainLength, 0.0f, 0.0f), "chain of " + chainLength + " lists");
    }

    @Test
    void testManyPushPopOperations() {
        // Stress test push/pop stack operations
        int list = createAndTrackList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);

        // Go deep into the stack
        final int stackDepth = 30;  // GL minimum is 32
        for (int i = 0; i < stackDepth; i++) {
            GLSMMatrices.glPushMatrix();
            GLSMMatrices.glTranslatef(1.0f, 0.0f, 0.0f);
        }

        // Pop all the way back
        for (int i = 0; i < stackDepth; i++) {
            GLSMMatrices.glPopMatrix();
        }

        // Add final known transform
        GLSMMatrices.glTranslatef(99.0f, 0.0f, 0.0f);

        GLSMDraws.glEndList();

        assertEquals(99.0f, modelviewAfterCall(list).get(12), 0.0001f, "After balanced push/pop, only final transform should remain");
    }

    @Test
    void testRapidListSwitching() {
        // Create multiple lists and rapidly switch between them
        final int numLists = 20;
        int[] lists = new int[numLists];

        // Create all lists with unique transforms
        for (int i = 0; i < numLists; i++) {
            lists[i] = createAndTrackList();
            GLSMDraws.glNewList(lists[i], GL11.GL_COMPILE);
            GLSMMatrices.glTranslatef(i + 1, 0, 0);  // List i translates by (i+1)
            GLSMDraws.glEndList();
        }

        // Rapidly switch between calling different lists
        for (int iteration = 0; iteration < 100; iteration++) {
            for (int i = 0; i < numLists; i++) {
                assertEquals(i + 1, modelviewAfterCall(lists[i]).get(12), 0.0001f, "List " + i + " should translate by " + (i + 1));
            }
        }
    }

    @Test
    void testRecompileSameId() {
        // Test recompiling a display list with the same ID
        int list = createAndTrackList();

        // First compilation
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMMatrices.glTranslatef(5.0f, 0.0f, 0.0f);
        GLSMDraws.glEndList();

        assertEquals(5.0f, modelviewAfterCall(list).get(12), 0.0001f, "First compilation should translate by 5");

        // Recompile with different transform
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMMatrices.glTranslatef(10.0f, 0.0f, 0.0f);
        GLSMDraws.glEndList();

        assertEquals(10.0f, modelviewAfterCall(list).get(12), 0.0001f, "Recompilation should translate by 10");
    }

    @Test
    void testComplexTransformSequence() {
        // Test a complex sequence of transforms that could expose numerical issues
        int list = createAndTrackList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);

        // Apply transforms that together should result in a known value
        GLSMMatrices.glTranslatef(100.0f, 0.0f, 0.0f);
        GLSMMatrices.glRotatef(90.0f, 0.0f, 0.0f, 1.0f);
        GLSMMatrices.glTranslatef(50.0f, 0.0f, 0.0f);  // This goes in Y direction after rotate
        GLSMMatrices.glRotatef(-90.0f, 0.0f, 0.0f, 1.0f);  // Cancel the rotation
        GLSMMatrices.glTranslatef(25.0f, 0.0f, 0.0f);

        GLSMDraws.glEndList();

        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();
        GLSMDraws.glCallList(list);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GLSMQueries.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Build expected with JOML
        Matrix4f expected = new Matrix4f()
            .translate(100.0f, 0.0f, 0.0f)
            .rotate((float) Math.toRadians(90.0f), 0.0f, 0.0f, 1.0f)
            .translate(50.0f, 0.0f, 0.0f)
            .rotate((float) Math.toRadians(-90.0f), 0.0f, 0.0f, 1.0f)
            .translate(25.0f, 0.0f, 0.0f);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.001f,
                String.format("Matrix[%d] mismatch in complex transform", i));
        }

        GLSMMatrices.glPopMatrix();
    }
}
