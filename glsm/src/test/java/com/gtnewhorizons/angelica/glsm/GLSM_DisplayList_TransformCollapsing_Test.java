package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.assertMatrixEquals;
import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.immediateQuad;
import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.modelviewAfterCall;
import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.rawQuad;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for transform collapsing optimization in display lists. */
@GLCompatTest
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

        Int2IntMap counts = compiled.getCommandCounts();
        int multMatrixCount = counts.getOrDefault(GLCommand.MULT_MATRIX, 0);
        int individualTransformCount = counts.getOrDefault(GLCommand.TRANSLATE, 0)
            + counts.getOrDefault(GLCommand.SCALE, 0);

        assertEquals(1, multMatrixCount,
            "5 consecutive transforms should collapse to 1 MultMatrix");
        assertEquals(0, individualTransformCount,
            "No individual transform commands should remain in optimized list");

        // Verify behavioral correctness: matrix state should match expected
        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        Matrix4f expected = new Matrix4f()
            .translate(1.0f, 2.0f, 3.0f)
            .rotate((float) Math.toRadians(45.0f), 0.0f, 1.0f, 0.0f)
            .scale(2.0f, 2.0f, 2.0f)
            .translate(-0.5f, -0.5f, -0.5f)
            .rotate((float) Math.toRadians(30.0f), 1.0f, 0.0f, 0.0f);

        assertMatrixEquals(expected, actualBuf, "mismatch after collapsed transforms");
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
        Int2IntMap counts = compiled.getCommandCounts();

        int pushCount = counts.getOrDefault(GLCommand.PUSH_MATRIX, 0);
        int popCount = counts.getOrDefault(GLCommand.POP_MATRIX, 0);

        assertEquals(1, pushCount, "Should have exactly 1 PushMatrix");
        assertEquals(1, popCount, "Should have exactly 1 PopMatrix");

        // Verify behavioral correctness
        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        // Expected: translate(10,0,0) then translate(0,3,0) = translate(10,3,0)
        // The inner push/pop isolates the (5,0,0) translate and rotation
        Matrix4f expected = new Matrix4f()
            .translate(10.0f, 0.0f, 0.0f)
            .translate(0.0f, 3.0f, 0.0f);

        assertMatrixEquals(expected, actualBuf, "mismatch - push/pop should isolate inner transforms");
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
        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        // Final state: translate(1,0,0) + translate(5,0,0) = translate(6,0,0)
        // Level 1 and 2 transforms are isolated by push/pop
        Matrix4f expected = new Matrix4f()
            .translate(1.0f, 0.0f, 0.0f)
            .translate(5.0f, 0.0f, 0.0f);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        expected.get(expectedBuf);

        assertEquals(expectedBuf.get(12), actualBuf.get(12), 0.0001f,
            "X translation should be 6.0 (1+5, inner transforms isolated)");
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
        IntList opcodes = compiled.getCommandOpcodes();

        // Find positions of MultMatrix and CallList
        int multMatrixIndex = opcodes.indexOf(GLCommand.MULT_MATRIX);
        int callListIndex = opcodes.indexOf(GLCommand.CALL_LIST);

        assertTrue(multMatrixIndex >= 0, "Should have MultMatrix command");
        assertTrue(callListIndex >= 0, "Should have CallList command");
        assertTrue(multMatrixIndex < callListIndex,
            "MultMatrix should be emitted BEFORE CallList (transform flush at barrier)");

        // Verify behavioral correctness
        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        // Build expected: translate(10,0,0) * rotate(90,Y) * translate(100,0,0) * translate(1,0,0)
        // After 90-degree Y rotation, X axis points into -Z, so nested translate goes along -Z
        Matrix4f expected = new Matrix4f()
            .translate(10.0f, 0.0f, 0.0f)
            .rotate((float) Math.toRadians(90.0f), 0.0f, 1.0f, 0.0f)
            .translate(100.0f, 0.0f, 0.0f)
            .translate(1.0f, 0.0f, 0.0f);

        assertMatrixEquals(expected, actualBuf, "mismatch - nested list should see parent's transformed state");
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

        // If compiled is null, the list is empty (correct - no commands needed)
        // If compiled exists, verify no MultMatrix commands
        int multMatrixCount = 0;
        if (compiled != null) {
            Int2IntMap counts = compiled.getCommandCounts();
            multMatrixCount = counts.getOrDefault(GLCommand.MULT_MATRIX, 0);
        }

        assertEquals(0, multMatrixCount,
            "Identity-equivalent transforms should not emit MultMatrix");
    }

    static Stream<Arguments> discardedByLoadIdentity() {
        return Stream.of(
            Arguments.of("accumulated translate", (Runnable) () -> GLStateManager.glTranslatef(100.0f, 100.0f, 100.0f)),
            Arguments.of("loaded matrix", (Runnable) () -> {
                final FloatBuffer loadBuf = BufferUtils.createFloatBuffer(16);
                new Matrix4f().translate(50.0f, 60.0f, 70.0f).rotate((float) Math.toRadians(45.0f), 1.0f, 1.0f, 0.0f).get(loadBuf);
                GLStateManager.glLoadMatrix(loadBuf);
            }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("discardedByLoadIdentity")
    void testLoadIdentityDiscardsEarlierTransforms(String name, Runnable earlier) {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        earlier.run();
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(1.0f, 2.0f, 3.0f);

        GLStateManager.glEndList();

        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        assertEquals(1.0f, actualBuf.get(12), 0.0001f, "X should be 1.0 (LoadIdentity reset)");
        assertEquals(2.0f, actualBuf.get(13), 0.0001f, "Y should be 2.0 (LoadIdentity reset)");
        assertEquals(3.0f, actualBuf.get(14), 0.0001f, "Z should be 3.0 (LoadIdentity reset)");
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
        Int2IntMap counts = compiled.getCommandCounts();

        int multMatrixCount = counts.getOrDefault(GLCommand.MULT_MATRIX, 0);

        assertEquals(1, multMatrixCount,
            "Translate + MultMatrix + Translate should collapse to 1 MultMatrix");

        // Verify behavioral correctness
        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        Matrix4f expected = new Matrix4f()
            .translate(1.0f, 0.0f, 0.0f)
            .rotate((float) Math.toRadians(90.0f), 0.0f, 1.0f, 0.0f)
            .translate(2.0f, 0.0f, 0.0f);

        assertMatrixEquals(expected, actualBuf, "mismatch for MultMatrix accumulation");
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

        // Verify the LOAD_MATRIX command is present
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled, "Display list should be compiled");

        Int2IntMap counts = compiled.getCommandCounts();
        int loadMatrixCount = counts.getOrDefault(GLCommand.LOAD_MATRIX, 0);

        assertEquals(1, loadMatrixCount,
            "Should have exactly 1 LoadMatrix command (can't be absorbed)");

        // Verify behavioral correctness: matrix should equal the loaded matrix
        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        assertMatrixEquals(loadedMatrix, actualBuf, "should match loaded matrix (not multiplied)");
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
        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        // Expected: loadedMatrix * translate(10,0,0)
        Matrix4f expected = new Matrix4f(loadedMatrix).translate(10.0f, 0.0f, 0.0f);
        assertMatrixEquals(expected, actualBuf, "transform after LoadMatrix should be relative to loaded matrix");
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
        final FloatBuffer actualBuf = modelviewAfterCall(testList);

        assertEquals(0.0f, actualBuf.get(12), 0.0001f, "X should be 0.0 (from second LoadMatrix)");
        assertEquals(20.0f, actualBuf.get(13), 0.0001f, "Y should be 20.0 (from second LoadMatrix)");
    }

    // ==================== Immediate Mode with Transforms Tests ====================

    @Test
    void testImmediateModeDrawCommandStructure() {
        // Verifies MultMatrix emitted before DrawRange, push/pop ordering correct
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glPushMatrix();
        GLStateManager.glScalef(0.25f, 0.25f, 0.25f);

        immediateQuad(2.0f);

        GLStateManager.glPopMatrix();
        GLStateManager.glEndList();

        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled);

        IntList opcodes = compiled.getCommandOpcodes();
        int popIndex = opcodes.indexOf(GLCommand.POP_MATRIX);
        int scaleIndex = opcodes.indexOf(GLCommand.SCALE);
        int drawRangeIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);

        assertTrue(scaleIndex == -1, "Scale gets prebaked");
        assertTrue(drawRangeIndex < popIndex, "DrawRange before Pop");
    }

    @Test
    void testImmediateModeDrawMatrixValues() {
        // Verifies matrix values correct after playback with translate + scale
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glTranslatef(100.0f, 50.0f, 0.0f);
        GLStateManager.glScalef(0.5f, 0.5f, 1.0f);

        immediateQuad(16.0f);

        GLStateManager.glEndList();

        // Verify transforms collapsed
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        Int2IntMap counts = compiled.getCommandCounts();
        assertEquals(1, counts.getOrDefault(GLCommand.MULT_MATRIX, 0), "Translate+Scale -> 1 MultMatrix");
        assertEquals(0, counts.getOrDefault(GLCommand.TRANSLATE, 0), "No individual Translate");
        assertEquals(0, counts.getOrDefault(GLCommand.SCALE, 0), "No individual Scale");

        // Verify matrix values after playback
        final FloatBuffer matrixBuf = modelviewAfterCall(testList);

        assertEquals(0.5f, matrixBuf.get(0), 0.0001f, "Scale X");
        assertEquals(0.5f, matrixBuf.get(5), 0.0001f, "Scale Y");
        assertEquals(1.0f, matrixBuf.get(10), 0.0001f, "Scale Z");
        assertEquals(100.0f, matrixBuf.get(12), 0.0001f, "Translate X");
        assertEquals(50.0f, matrixBuf.get(13), 0.0001f, "Translate Y");
    }

    // ==================== Cross-Mode Transform Collapsing Tests ====================

    @Test
    void testTransformsAcrossMatrixModeSwitch() {
        // Transforms must not leak across matrix mode boundaries
        // Compute expected results via GLSM (same path as display list playback)
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);
        GLStateManager.glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glScalef(2.0f, 2.0f, 2.0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glTranslatef(3.0f, 0.0f, 0.0f);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        FloatBuffer expectedMV = BufferUtils.createFloatBuffer(16);
        FloatBuffer expectedProj = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, expectedMV);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glGetFloat(GL11.GL_PROJECTION_MATRIX, expectedProj);

        // Reset and test via display list
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glTranslatef(1.0f, 0.0f, 0.0f);
        GLStateManager.glRotatef(45.0f, 0.0f, 1.0f, 0.0f);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glScalef(2.0f, 2.0f, 2.0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glTranslatef(3.0f, 0.0f, 0.0f);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glEndList();

        GLStateManager.glCallList(testList);

        FloatBuffer actualMV = BufferUtils.createFloatBuffer(16);
        FloatBuffer actualProj = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualMV);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glGetFloat(GL11.GL_PROJECTION_MATRIX, actualProj);

        for (int i = 0; i < 16; i++) {
            assertEquals(expectedMV.get(i), actualMV.get(i), 0.0001f, "MODELVIEW[" + i + "]");
            assertEquals(expectedProj.get(i), actualProj.get(i), 0.0001f, "PROJECTION[" + i + "]");
        }

        // Cleanup
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void testBarrierCommandsBetweenScaleAndDraw() {
        // State commands between transform and draw must not break transform capture
        for (int mode : new int[]{GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE}) {
            if (testList > 0) {
                GLStateManager.glDeleteLists(testList, 1);
            }

            testList = GL11.glGenLists(1);
            GLStateManager.glNewList(testList, mode);

            GLStateManager.glScalef(0.5f, 0.5f, 1.0f);
            GLStateManager.glTranslatef(2, 2, 2);
            // Barrier commands between scale and draw
            GLStateManager.glDepthMask(true);
            GLStateManager.disableLighting();
            GLStateManager.enableTexture();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

            immediateQuad(1.0f);

            GLStateManager.glEndList();

            CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
            assertNotNull(compiled);

            Int2IntMap counts = compiled.getCommandCounts();
            assertEquals(1, counts.getOrDefault(GLCommand.MULT_MATRIX, 0), "1 MultMatrix for mode " + mode);
            assertEquals(1, counts.getOrDefault(GLCommand.DRAW_RANGE, 0), "1 DrawRange for mode " + mode);

            IntList opcodes = compiled.getCommandOpcodes();
            assertTrue(opcodes.indexOf(GLCommand.MULT_MATRIX) > opcodes.indexOf(GLCommand.DRAW_RANGE),
                "MultMatrix after DrawRange for mode " + mode);

            final FloatBuffer matrixBuf = modelviewAfterCall(testList);
            assertEquals(0.5f, matrixBuf.get(0), 0.0001f, "Scale X for mode " + mode);
            assertEquals(0.5f, matrixBuf.get(5), 0.0001f, "Scale Y for mode " + mode);
        }
    }

    @Test
    void testRawTessellatorDrawPlaysBackWithTheTransform() {
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glScalef(0.5f, 0.5f, 0.5f);

        rawQuad(1.0f);

        GLStateManager.glEndList();

        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled);

        final FloatBuffer matrixBuf = modelviewAfterCall(testList);
        assertEquals(0.5f, matrixBuf.get(0), 0.0001f, "Scale X after playback");
        assertEquals(0.5f, matrixBuf.get(5), 0.0001f, "Scale Y after playback");
    }

    // ==================== Optimizer Push/Pop Reset Tests ====================

    @Test
    void testOptimizerResetsAfterPushMatrix() {
        // Optimizer must reset after PUSH_MATRIX to avoid spurious inverse transform emission
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        // T1: transforms before push (collapsed)
        GLStateManager.glTranslatef(5.0f, 0.0f, 0.0f);
        GLStateManager.glScalef(2.0f, 2.0f, 2.0f);

        GLStateManager.glPushMatrix();

        // T2: transforms after push (collapsed separately)
        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);
        GLStateManager.glRotatef(45.0f, 0.0f, 1.0f, 0.0f);

        immediateQuad(1.0f);

        GLStateManager.glPopMatrix();
        GLStateManager.glEndList();

        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled);

        Int2IntMap counts = compiled.getCommandCounts();
        IntList opcodes = compiled.getCommandOpcodes();

        // Should have exactly 1 MULT_MATRIX (T1 before push, T2 after push is already prebaked, will get discarded.)
        // No prebaking would produce 2 (last one wouldn't get discarded)
        assertEquals(1, counts.getOrDefault(GLCommand.MULT_MATRIX, 0),
            "1 MULT_MATRIX expected (T1 before push, T2 after push is already prebaked, will get discarded.)");

        // Verify ordering
        int firstMultMatrix = opcodes.indexOf(GLCommand.MULT_MATRIX);
        int pushIndex = opcodes.indexOf(GLCommand.PUSH_MATRIX);
        int drawIndex = opcodes.indexOf(GLCommand.DRAW_RANGE);
        int popIndex = opcodes.indexOf(GLCommand.POP_MATRIX);

        assertTrue(firstMultMatrix < pushIndex, "T1 before PUSH");
        assertTrue(pushIndex < drawIndex, "PUSH before DRAW");
        assertTrue(drawIndex < popIndex, "DRAW before POP");

        // Verify final matrix after pop = T1
        final FloatBuffer matrixBuf = modelviewAfterCall(testList);

        assertEquals(2.0f, matrixBuf.get(0), 0.0001f, "Scale X after pop");
        assertEquals(2.0f, matrixBuf.get(5), 0.0001f, "Scale Y after pop");
        assertEquals(5.0f, matrixBuf.get(12), 0.0001f, "Translate X after pop");
    }

    @Test
    void testTransformCollapsingEquivalence() {
        // Core equivalence test: verify that collapsed transforms produce
        // the exact same result as executing transforms sequentially.
        //
        // This tests the fundamental invariant of the optimization.
        // Use axis-aligned rotations to minimize float/double precision differences.

        // Transform sequence: translate, rotate around Y, scale, rotate around X, translate
        final float tx1 = 5.0f, ty1 = 3.0f, tz1 = -2.0f;
        final float angle1 = 45.0f;
        final float sx = 1.5f, sy = 0.8f, sz = 2.0f;
        final float angle2 = 30.0f;
        final float tx2 = -1.0f, ty2 = 4.0f, tz2 = 0.5f;

        // Execute directly via GLSM
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(tx1, ty1, tz1);
        GLStateManager.glRotatef(angle1, 0.0f, 1.0f, 0.0f);  // Y-axis rotation
        GLStateManager.glScalef(sx, sy, sz);
        GLStateManager.glRotatef(angle2, 1.0f, 0.0f, 0.0f);  // X-axis rotation
        GLStateManager.glTranslatef(tx2, ty2, tz2);

        FloatBuffer expectedBuf = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, expectedBuf);

        // Reset and execute via display list
        GLStateManager.glLoadIdentity();

        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
        GLStateManager.glTranslatef(tx1, ty1, tz1);
        GLStateManager.glRotatef(angle1, 0.0f, 1.0f, 0.0f);
        GLStateManager.glScalef(sx, sy, sz);
        GLStateManager.glRotatef(angle2, 1.0f, 0.0f, 0.0f);
        GLStateManager.glTranslatef(tx2, ty2, tz2);
        GLStateManager.glEndList();

        GLStateManager.glCallList(testList);

        FloatBuffer actualBuf = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, actualBuf);

        // Verify equivalence
        for (int i = 0; i < 16; i++) {
            assertEquals(expectedBuf.get(i), actualBuf.get(i), 0.0001f,
                String.format("Matrix[%d]: collapsed transform must equal sequential execution", i));
        }

        GLStateManager.glLoadIdentity();
    }
}
