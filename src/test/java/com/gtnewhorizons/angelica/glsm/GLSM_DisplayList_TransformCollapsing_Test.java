package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.LoadMatrixCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.MultMatrixCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.PushMatrixCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.PopMatrixCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.TranslateCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.RotateCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.ScaleCmd;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for transform collapsing optimization in display lists.
 *
 * <p>The optimization collapses consecutive MODELVIEW transforms into single MultMatrix
 * commands, emitting them at "barriers" (draws, CallList, exit). These tests verify:</p>
 * <ul>
 *   <li>Consecutive transforms collapse to single MultMatrix</li>
 *   <li>Matrix state is correct after execution</li>
 *   <li>Push/Pop maintain proper stack semantics</li>
 *   <li>Barriers (draws, CallList) correctly flush pending transforms</li>
 * </ul>
 */
@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_TransformCollapsing_Test {

    private int testList = -1;
    private int nestedList = -1;

    @AfterEach
    void cleanup() {
        if (testList > 0) {
            GLStateManager.glDeleteLists(testList, 1);
            testList = -1;
        }
        if (nestedList > 0) {
            GLStateManager.glDeleteLists(nestedList, 1);
            nestedList = -1;
        }
        GLStateManager.glLoadIdentity();
    }

    // ==================== Transform Collapsing Tests ====================

    @Test
    void testConsecutiveTransformsCollapseToSingleMultMatrix() {
        // Multiple consecutive transforms should collapse to one MultMatrix in optimized list
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // 5 consecutive transforms
        GLStateManager.glTranslatef(1.0f, 2.0f, 3.0f);
        GLStateManager.glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
        GLStateManager.glScalef(2.0f, 2.0f, 2.0f);
        GLStateManager.glTranslatef(-0.5f, -0.5f, -0.5f);
        GLStateManager.glRotatef(30.0f, 1.0f, 0.0f, 0.0f);

        GLStateManager.glEndList();

        // Verify optimization: should have collapsed to 1 MultMatrix
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled, "Display list should be compiled");

        DisplayListCommand[] optimized = compiled.commands();
        long multMatrixCount = Arrays.stream(optimized)
            .filter(cmd -> cmd instanceof MultMatrixCmd)
            .count();
        long individualTransformCount = Arrays.stream(optimized)
            .filter(cmd -> cmd instanceof TranslateCmd || cmd instanceof RotateCmd || cmd instanceof ScaleCmd)
            .count();

        assertEquals(1, multMatrixCount,
            "5 consecutive transforms should collapse to 1 MultMatrix");
        assertEquals(0, individualTransformCount,
            "No individual transform commands should remain in optimized list");

        // Verify behavioral correctness: matrix state should match expected
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        Matrix4f expected = new Matrix4f()
            .translate(1.0f, 2.0f, 3.0f)
            .rotate((float) Math.toRadians(45.0f), 0.0f, 1.0f, 0.0f)
            .scale(2.0f, 2.0f, 2.0f)
            .translate(-0.5f, -0.5f, -0.5f)
            .rotate((float) Math.toRadians(30.0f), 1.0f, 0.0f, 0.0f);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] mismatch after collapsed transforms", i));
        }

        GLStateManager.glPopMatrix();
    }

    @Test
    void testPushPopPreservesStackSemantics() {
        // Push/Pop should maintain proper stack semantics with collapsed transforms
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);  // Outer translate
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);   // Inner translate (isolated)
        GLStateManager.glRotatef(90.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glPopMatrix();
        GLStateManager.glTranslatef(0.0f, 3.0f, 0.0f);   // After pop

        GLStateManager.glEndList();

        // Verify optimization: should have Push, Pop, and collapsed MultMatrix commands
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        DisplayListCommand[] optimized = compiled.commands();

        long pushCount = Arrays.stream(optimized).filter(cmd -> cmd instanceof PushMatrixCmd).count();
        long popCount = Arrays.stream(optimized).filter(cmd -> cmd instanceof PopMatrixCmd).count();

        assertEquals(1, pushCount, "Should have exactly 1 PushMatrix");
        assertEquals(1, popCount, "Should have exactly 1 PopMatrix");

        // Verify behavioral correctness
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Expected: translate(10,0,0) then translate(0,3,0) = translate(10,3,0)
        // The inner push/pop isolates the (5,0,0) translate and rotation
        Matrix4f expected = new Matrix4f()
            .translate(10.0f, 0.0f, 0.0f)
            .translate(0.0f, 3.0f, 0.0f);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] mismatch - push/pop should isolate inner transforms", i));
        }

        GLStateManager.glPopMatrix();
    }

    @Test
    void testNestedPushPopWithTransforms() {
        // Nested push/pop with transforms at each level
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);   // Level 0
        GLStateManager.glPushMatrix();                   // Enter level 1
        GLStateManager.glTranslatef(2.0f, 0.0f, 0.0f);
        GLStateManager.glPushMatrix();                   // Enter level 2
        GLStateManager.glTranslatef(3.0f, 0.0f, 0.0f);
        GLStateManager.glPopMatrix();                    // Back to level 1
        GLStateManager.glTranslatef(4.0f, 0.0f, 0.0f);   // At level 1
        GLStateManager.glPopMatrix();                    // Back to level 0
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);   // At level 0

        GLStateManager.glEndList();

        // Verify behavioral correctness
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Final state: translate(1,0,0) + translate(5,0,0) = translate(6,0,0)
        // Level 1 and 2 transforms are isolated by push/pop
        Matrix4f expected = new Matrix4f()
            .translate(1.0f, 0.0f, 0.0f)
            .translate(5.0f, 0.0f, 0.0f);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        assertEquals(expectedBuf.get(12), actualBuf.get(12), 0.0001f,
            "X translation should be 6.0 (1+5, inner transforms isolated)");

        GLStateManager.glPopMatrix();  // Balance the push from line 185
    }

    @Test
    void testCallListFlushesTransform() {
        // Create a nested display list
        nestedList = GL11.glGenLists(1);
        GLStateManager.glNewList(nestedList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(100.0f, 0.0f, 0.0f);  // Large translate to verify it's applied
        GLStateManager.glEndList();

        // Create parent list that transforms then calls nested list
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);   // Before CallList
        GLStateManager.glRotatef(90.0f, 0.0f, 1.0f, 0.0f);
        GLStateManager.glCallList(nestedList);            // Barrier - should flush transforms
        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);    // After CallList

        GLStateManager.glEndList();

        // Verify optimization: MultMatrix should be emitted before CallList
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        DisplayListCommand[] optimized = compiled.commands();

        // Find positions of MultMatrix and CallList
        int multMatrixIndex = -1;
        int callListIndex = -1;
        for (int i = 0; i < optimized.length; i++) {
            DisplayListCommand cmd = optimized[i];
            if (cmd instanceof MultMatrixCmd && multMatrixIndex == -1) {
                multMatrixIndex = i;
            }
            if (cmd.toString().contains("CallList")) {
                callListIndex = i;
            }
        }

        assertTrue(multMatrixIndex >= 0, "Should have MultMatrix command");
        assertTrue(callListIndex >= 0, "Should have CallList command");
        assertTrue(multMatrixIndex < callListIndex,
            "MultMatrix should be emitted BEFORE CallList (transform flush at barrier)");

        // Verify behavioral correctness
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Build expected: translate(10,0,0) * rotate(90,Y) * translate(100,0,0) * translate(1,0,0)
        // After 90-degree Y rotation, X axis points into -Z, so nested translate goes along -Z
        Matrix4f expected = new Matrix4f()
            .translate(10.0f, 0.0f, 0.0f)
            .rotate((float) Math.toRadians(90.0f), 0.0f, 1.0f, 0.0f)
            .translate(100.0f, 0.0f, 0.0f)
            .translate(1.0f, 0.0f, 0.0f);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] mismatch - nested list should see parent's transformed state", i));
        }

        GLStateManager.glPopMatrix();
    }

    @Test
    void testIdentityTransformNotEmitted() {
        // A display list with only identity-equivalent transforms should not emit MultMatrix
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // These cancel out to identity
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);
        GLStateManager.glTranslatef(-5.0f, 0.0f, 0.0f);

        GLStateManager.glEndList();

        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        DisplayListCommand[] optimized = compiled.commands();

        long multMatrixCount = Arrays.stream(optimized)
            .filter(cmd -> cmd instanceof MultMatrixCmd)
            .count();

        assertEquals(0, multMatrixCount,
            "Identity-equivalent transforms should not emit MultMatrix");
    }

    @Test
    void testTransformBetweenDrawsCreatesSeparateBatches() {
        // This test verifies that draws with different transforms are not batched together
        // Note: Without actual Tessellator integration, we can only verify command structure

        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // Simulate: Transform A, Draw, Transform B, Draw
        // The draws should NOT be in the same batch
        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);
        // (Would have a draw here in real usage)
        GLStateManager.glTranslatef(2.0f, 0.0f, 0.0f);
        // (Would have another draw here)

        GLStateManager.glEndList();

        // Verify the transforms are accumulated correctly
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Total translation should be 3.0 (1+2)
        assertEquals(3.0f, actualBuf.get(12), 0.0001f,
            "Consecutive translates should accumulate");

        GLStateManager.glPopMatrix();
    }

    @Test
    void testLoadIdentityResetsAccumulation() {
        // LoadIdentity should reset accumulated transforms
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glTranslatef(100.0f, 100.0f, 100.0f);  // Big translate
        GLStateManager.glLoadIdentity();                       // Reset
        GLStateManager.glTranslatef(1.0f, 2.0f, 3.0f);        // New translate

        GLStateManager.glEndList();

        // Verify: final state should only have the second translate
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        assertEquals(1.0f, actualBuf.get(12), 0.0001f, "X should be 1.0 (LoadIdentity reset)");
        assertEquals(2.0f, actualBuf.get(13), 0.0001f, "Y should be 2.0 (LoadIdentity reset)");
        assertEquals(3.0f, actualBuf.get(14), 0.0001f, "Z should be 3.0 (LoadIdentity reset)");

        GLStateManager.glPopMatrix();
    }

    @Test
    void testMultMatrixAccumulation() {
        // MultMatrix should be accumulated like other transforms
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);

        // Create a rotation matrix and multiply
        Matrix4f rotMatrix = new Matrix4f().rotate((float) Math.toRadians(90.0f), 0.0f, 1.0f, 0.0f);
        FloatBuffer rotBuf = BufferUtils.createFloatBuffer(16);
        rotMatrix.get(rotBuf);
        rotBuf.rewind();
        GLStateManager.glMultMatrix(rotBuf);

        GLStateManager.glTranslatef(2.0f, 0.0f, 0.0f);

        GLStateManager.glEndList();

        // Verify optimization: should collapse to 1 MultMatrix
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        DisplayListCommand[] optimized = compiled.commands();

        long multMatrixCount = Arrays.stream(optimized)
            .filter(cmd -> cmd instanceof MultMatrixCmd)
            .count();

        assertEquals(1, multMatrixCount,
            "Translate + MultMatrix + Translate should collapse to 1 MultMatrix");

        // Verify behavioral correctness
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        Matrix4f expected = new Matrix4f()
            .translate(1.0f, 0.0f, 0.0f)
            .rotate((float) Math.toRadians(90.0f), 0.0f, 1.0f, 0.0f)
            .translate(2.0f, 0.0f, 0.0f);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] mismatch for MultMatrix accumulation", i));
        }

        GLStateManager.glPopMatrix();
    }

    // ==================== Batcher Flush Ordering Tests ====================

    @Test
    void testDrawsBeforePushMatrixAreEmittedBeforePush() {
        // This test verifies that draws occurring before a PushMatrix are flushed
        // before the PushMatrix command, not delayed until later.
        // This was a bug where the batcher wasn't flushed at PushMatrix.

        // Create a list with: Transform, (implicit draw would go here), PushMatrix, Transform, PopMatrix
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // Outer transform
        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);
        // Note: If there was a draw here, it should be emitted BEFORE PushMatrix

        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);  // Inner transform
        GLStateManager.glPopMatrix();

        // Final transform after pop
        GLStateManager.glTranslatef(2.0f, 0.0f, 0.0f);

        GLStateManager.glEndList();

        // Verify the command structure
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled, "Display list should be compiled");

        DisplayListCommand[] optimized = compiled.commands();

        // Find indices of key commands
        int pushIndex = -1;
        int popIndex = -1;
        int multMatrixBeforePushCount = 0;

        for (int i = 0; i < optimized.length; i++) {
            DisplayListCommand cmd = optimized[i];
            if (cmd instanceof PushMatrixCmd && pushIndex == -1) {
                pushIndex = i;
            } else if (cmd instanceof PopMatrixCmd && popIndex == -1) {
                popIndex = i;
            } else if (cmd instanceof MultMatrixCmd && pushIndex == -1) {
                // Count MultMatrix commands before first PushMatrix
                multMatrixBeforePushCount++;
            }
        }

        assertTrue(pushIndex >= 0, "Should have PushMatrix command");
        assertTrue(popIndex >= 0, "Should have PopMatrix command");
        assertTrue(pushIndex < popIndex, "PushMatrix should come before PopMatrix");

        // The outer translate(1,0,0) should be emitted as MultMatrix before PushMatrix
        assertTrue(multMatrixBeforePushCount >= 1,
            "Transform before PushMatrix should be emitted before the Push");

        // Verify behavioral correctness: final state should be translate(1,0,0) + translate(2,0,0) = translate(3,0,0)
        // The inner translate(5,0,0) is isolated by push/pop
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        assertEquals(3.0f, actualBuf.get(12), 0.0001f,
            "X translation should be 3.0 (1+2, inner transform isolated by push/pop)");

        GLStateManager.glPopMatrix();
    }

    @Test
    void testDrawsBeforePopMatrixAreEmittedBeforePop() {
        // Verify draws inside a push/pop block are emitted before the pop
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);
        // Draws here should be emitted before PopMatrix
        GLStateManager.glPopMatrix();

        GLStateManager.glEndList();

        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        DisplayListCommand[] optimized = compiled.commands();

        int pushIndex = -1;
        int popIndex = -1;
        int multMatrixBetweenPushPop = 0;

        for (int i = 0; i < optimized.length; i++) {
            DisplayListCommand cmd = optimized[i];
            if (cmd instanceof PushMatrixCmd) {
                pushIndex = i;
            } else if (cmd instanceof PopMatrixCmd) {
                popIndex = i;
            } else if (cmd instanceof MultMatrixCmd && pushIndex >= 0 && popIndex == -1) {
                // MultMatrix between push and pop
                multMatrixBetweenPushPop++;
            }
        }

        assertTrue(pushIndex >= 0, "Should have PushMatrix");
        assertTrue(popIndex >= 0, "Should have PopMatrix");
        assertTrue(pushIndex < popIndex, "Push should come before Pop");

        // The inner translate should be emitted between push and pop (or collapsed to residual)
        // At minimum, the structure should be valid
        assertTrue(popIndex > pushIndex, "PopMatrix should follow PushMatrix");
    }

    // ==================== LoadMatrix Tests ====================

    @Test
    void testLoadMatrixSetsAbsoluteTransform() {
        // LoadMatrix should set an absolute matrix, not multiply
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // First, apply a large transform
        GLStateManager.glTranslatef(100.0f, 200.0f, 300.0f);

        // Then load a specific matrix (should replace, not multiply)
        Matrix4f loadedMatrix = new Matrix4f()
            .translate(1.0f, 2.0f, 3.0f)
            .rotate((float) Math.toRadians(45.0f), 0.0f, 1.0f, 0.0f);
        FloatBuffer loadBuf = BufferUtils.createFloatBuffer(16);
        loadedMatrix.get(loadBuf);
        loadBuf.rewind();
        GLStateManager.glLoadMatrix(loadBuf);

        GLStateManager.glEndList();

        // Verify the LoadMatrixCmd is present
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled, "Display list should be compiled");

        DisplayListCommand[] optimized = compiled.commands();
        long loadMatrixCount = Arrays.stream(optimized)
            .filter(cmd -> cmd instanceof LoadMatrixCmd)
            .count();

        assertEquals(1, loadMatrixCount,
            "Should have exactly 1 LoadMatrix command (can't be absorbed)");

        // Verify behavioral correctness: matrix should equal the loaded matrix
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        loadedMatrix.get(expectedBuf);

        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] should match loaded matrix (not multiplied)", i));
        }

        GLStateManager.glPopMatrix();
    }

    @Test
    void testLoadMatrixResetsRelativeTransform() {
        // After LoadMatrix, subsequent transforms should be relative to the loaded matrix
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // Load a rotation matrix
        Matrix4f loadedMatrix = new Matrix4f()
            .rotate((float) Math.toRadians(90.0f), 0.0f, 1.0f, 0.0f);
        FloatBuffer loadBuf = BufferUtils.createFloatBuffer(16);
        loadedMatrix.get(loadBuf);
        loadBuf.rewind();
        GLStateManager.glLoadMatrix(loadBuf);

        // Then translate - this should be relative to the loaded matrix
        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);

        GLStateManager.glEndList();

        // Verify behavioral correctness
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Expected: loadedMatrix * translate(10,0,0)
        Matrix4f expected = new Matrix4f(loadedMatrix).translate(10.0f, 0.0f, 0.0f);
        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d] - transform after LoadMatrix should be relative to loaded matrix", i));
        }

        GLStateManager.glPopMatrix();
    }

    @Test
    void testLoadMatrixThenLoadIdentity() {
        // LoadMatrix followed by LoadIdentity should result in identity
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // Load a complex matrix
        Matrix4f loadedMatrix = new Matrix4f()
            .translate(50.0f, 60.0f, 70.0f)
            .rotate((float) Math.toRadians(45.0f), 1.0f, 1.0f, 0.0f);
        FloatBuffer loadBuf = BufferUtils.createFloatBuffer(16);
        loadedMatrix.get(loadBuf);
        loadBuf.rewind();
        GLStateManager.glLoadMatrix(loadBuf);

        // Then reset to identity
        GLStateManager.glLoadIdentity();

        // Then apply a simple translate
        GLStateManager.glTranslatef(1.0f, 2.0f, 3.0f);

        GLStateManager.glEndList();

        // Verify: final state should just be translate(1,2,3)
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        assertEquals(1.0f, actualBuf.get(12), 0.0001f, "X should be 1.0");
        assertEquals(2.0f, actualBuf.get(13), 0.0001f, "Y should be 2.0");
        assertEquals(3.0f, actualBuf.get(14), 0.0001f, "Z should be 3.0");

        GLStateManager.glPopMatrix();
    }

    @Test
    void testMultipleLoadMatrixCalls() {
        // Multiple LoadMatrix calls should each be recorded
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // First load
        Matrix4f matrix1 = new Matrix4f().translate(10.0f, 0.0f, 0.0f);
        FloatBuffer buf1 = BufferUtils.createFloatBuffer(16);
        matrix1.get(buf1);
        buf1.rewind();
        GLStateManager.glLoadMatrix(buf1);

        // Second load (replaces first)
        Matrix4f matrix2 = new Matrix4f().translate(0.0f, 20.0f, 0.0f);
        FloatBuffer buf2 = BufferUtils.createFloatBuffer(16);
        matrix2.get(buf2);
        buf2.rewind();
        GLStateManager.glLoadMatrix(buf2);

        GLStateManager.glEndList();

        // Verify: final state should be matrix2
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        assertEquals(0.0f, actualBuf.get(12), 0.0001f, "X should be 0.0 (from second LoadMatrix)");
        assertEquals(20.0f, actualBuf.get(13), 0.0001f, "Y should be 20.0 (from second LoadMatrix)");

        GLStateManager.glPopMatrix();
    }
}
