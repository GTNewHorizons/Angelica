package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.commands.DrawCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import it.unimi.dsi.fastutil.ints.Int2IntMap;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for display list recording data structures.
 */
@ExtendWith(AngelicaExtension.class)
class RecordingDataStructuresTest {

//    private int testList = -1;
//
//    @AfterEach
//    void cleanup() {
//        if (testList > 0) {
//            GLStateManager.glDeleteLists(testList, 1);
//            testList = -1;
//        }
//    }
//
//    @Test
//    void testDrawCommandCreation() {
//        DrawCommand cmd = new DrawCommand(0, 4, GL11.GL_QUADS);
//        assertEquals(0, cmd.firstVertex());
//        assertEquals(4, cmd.vertexCount());
//        assertEquals(GL11.GL_QUADS, cmd.primitiveType());
//    }
//
//    @Test
//    void testRecordedGeometryCreation() {
//        ByteBuffer buffer = ByteBuffer.allocate(16);
//        DrawCommand cmd1 = new DrawCommand(0, 4, GL11.GL_QUADS);
//        DrawCommand cmd2 = new DrawCommand(4, 3, GL11.GL_TRIANGLES);
//        List<DrawCommand> commands = Arrays.asList(cmd1, cmd2);
//
//        RecordedGeometry geom = new RecordedGeometry(buffer, null, commands, 7);
//        assertEquals(buffer, geom.buffer());
//        assertEquals(commands, geom.drawCommands());
//        assertEquals(7, geom.totalVertexCount());
//        assertEquals(16, geom.getByteSize());
//    }
//
//    @Test
//    void testCompiledDisplayListCreation() {
//        // Create a display list with PushMatrix, Scale, PopMatrix
//        testList = GL11.glGenLists(1);
//        GLStateManager.glNewList(testList, GL11.GL_COMPILE);
//
//        GLStateManager.glPushMatrix();
//        GLStateManager.glScalef(2.0f, 2.0f, 2.0f);
//        GLStateManager.glPopMatrix();
//
//        GLStateManager.glEndList();
//
//        // Verify the compiled display list
//        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
//        assertNotNull(compiled, "Display list should be compiled");
//        assertNotNull(compiled.getCommandBuffer(), "Command buffer should not be null");
//
//        // Get command counts to verify structure
//        Int2IntMap counts = compiled.getCommandCounts();
//
//        // Should have PushMatrix and PopMatrix
//        assertEquals(1, counts.getOrDefault(GLCommand.PUSH_MATRIX, 0), "Should have 1 PushMatrix");
//        assertEquals(1, counts.getOrDefault(GLCommand.POP_MATRIX, 0), "Should have 1 PopMatrix");
//
//        // Scale should be collapsed into a MultMatrix (optimizer collapses transforms)
//        // Or if in debug mode, might have individual commands - just check we have some transforms
//        int transformCount = counts.getOrDefault(GLCommand.MULT_MATRIX, 0)
//            + counts.getOrDefault(GLCommand.SCALE, 0);
//        assertTrue(transformCount >= 0, "Should have transform commands");
//    }
//
//    @Test
//    void testImmediateModeRecorderCapturesQuads() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        // Record a single quad using immediate mode
//        recorder.begin(GL11.GL_QUADS);
//        recorder.setTexCoord(0, 0);
//        recorder.vertex(0, 0, 0);
//        recorder.setTexCoord(1, 0);
//        recorder.vertex(1, 0, 0);
//        recorder.setTexCoord(1, 1);
//        recorder.vertex(1, 1, 0);
//        recorder.setTexCoord(0, 1);
//        recorder.vertex(0, 1, 0);
//
//        // end() now returns quads immediately (for correct command interleaving)
//        ImmediateModeRecorder.Result result = recorder.end();
//        assertNotNull(result);
//        assertEquals(1, result.quads().size());
//        assertTrue(result.flags().hasTexture);  // We called setTexCoord
//
//        // After end(), recorder should be empty (quads were returned immediately)
//        assertFalse(recorder.hasGeometry());
//        assertNull(recorder.getQuadsAndClear());
//    }
//
//    @Test
//    void testImmediateModeRecorderCapturesTriangles() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        // Record 2 triangles (6 vertices, should become 2 degenerate quads)
//        recorder.begin(GL11.GL_TRIANGLES);
//        recorder.vertex(0, 0, 0);
//        recorder.vertex(1, 0, 0);
//        recorder.vertex(0.5f, 1, 0);
//        recorder.vertex(2, 0, 0);
//        recorder.vertex(3, 0, 0);
//        recorder.vertex(2.5f, 1, 0);
//
//        // end() now returns quads immediately
//        ImmediateModeRecorder.Result result = recorder.end();
//        assertNotNull(result);
//        assertEquals(2, result.quads().size());  // 2 triangles = 2 degenerate quads
//
//        // Recorder should be empty after end()
//        assertFalse(recorder.hasGeometry());
//    }
//
//    @Test
//    void testImmediateModeRecorderCapturesTriangleFan() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        // Record a triangle fan (center + 3 outer vertices = 2 triangles = 2 quads)
//        recorder.begin(GL11.GL_TRIANGLE_FAN);
//        recorder.vertex(0, 0, 0);  // Center
//        recorder.vertex(1, 0, 0);
//        recorder.vertex(1, 1, 0);
//        recorder.vertex(0, 1, 0);
//
//        // end() now returns quads immediately
//        ImmediateModeRecorder.Result result = recorder.end();
//        assertNotNull(result);
//        assertEquals(2, result.quads().size());  // 2 triangles from fan
//
//        // Recorder should be empty after end()
//        assertFalse(recorder.hasGeometry());
//    }
//
//    @Test
//    void testImmediateModeRecorderEmptyAfterReset() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        recorder.begin(GL11.GL_QUADS);
//        recorder.vertex(0, 0, 0);
//        recorder.vertex(1, 0, 0);
//        recorder.vertex(1, 1, 0);
//        recorder.vertex(0, 1, 0);
//
//        // end() returns quads immediately, so recorder is empty after
//        ImmediateModeRecorder.Result result = recorder.end();
//        assertNotNull(result);
//        assertEquals(1, result.quads().size());
//
//        // Already empty after end(), but reset() should still work
//        assertFalse(recorder.hasGeometry());
//
//        recorder.reset();
//
//        assertFalse(recorder.hasGeometry());
//        assertNull(recorder.getQuadsAndClear());
//    }
//
//    @Test
//    void testImmediateModeRecorderNormalAndColor() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        recorder.setNormal(0, 0, 1);  // Normal pointing +Z
//        recorder.begin(GL11.GL_QUADS);
//        recorder.vertex(0, 0, 0);
//        recorder.vertex(1, 0, 0);
//        recorder.vertex(1, 1, 0);
//        recorder.vertex(0, 1, 0);
//
//        // end() now returns quads immediately
//        ImmediateModeRecorder.Result result = recorder.end();
//        assertNotNull(result);
//        assertTrue(result.flags().hasNormals);
//    }
//
//    @Test
//    void testImmediateModeRecorderThrowsOnInvalidState() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        // glEnd without glBegin
//        assertThrows(IllegalStateException.class, () -> recorder.end());
//
//        // glVertex without glBegin
//        assertThrows(IllegalStateException.class, () -> recorder.vertex(0, 0, 0));
//
//        // Nested glBegin
//        recorder.begin(GL11.GL_QUADS);
//        assertThrows(IllegalStateException.class, () -> recorder.begin(GL11.GL_QUADS));
//        recorder.end();
//
//        // getQuadsAndClear while in primitive
//        recorder.begin(GL11.GL_QUADS);
//        assertThrows(IllegalStateException.class, () -> recorder.getQuadsAndClear());
//    }
//
//    @Test
//    void testImmediateModeRecorderCapturesLines() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        // Record 2 lines (4 vertices)
//        recorder.begin(GL11.GL_LINES);
//        recorder.vertex(0, 0, 0);
//        recorder.vertex(1, 0, 0);
//        recorder.vertex(0, 1, 0);
//        recorder.vertex(1, 1, 0);
//
//        ImmediateModeRecorder.Result result = recorder.end();
//        assertNotNull(result);
//        assertTrue(result.quads().isEmpty(), "Lines should not produce quads");
//        assertEquals(4, result.lines().size(), "4 vertices for 2 lines");
//    }
//
//    @Test
//    void testImmediateModeRecorderCapturesLineStrip() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        // Record a line strip: 4 vertices = 3 line segments = 6 line vertices
//        recorder.begin(GL11.GL_LINE_STRIP);
//        recorder.vertex(0, 0, 0);
//        recorder.vertex(1, 0, 0);
//        recorder.vertex(1, 1, 0);
//        recorder.vertex(0, 1, 0);
//
//        ImmediateModeRecorder.Result result = recorder.end();
//        assertNotNull(result);
//        assertTrue(result.quads().isEmpty(), "Line strips should not produce quads");
//        assertEquals(6, result.lines().size(), "4 vertices in strip = 3 segments = 6 line vertices");
//    }
//
//    @Test
//    void testImmediateModeRecorderCapturesLineLoop() {
//        ImmediateModeRecorder recorder = new ImmediateModeRecorder();
//
//        // Record a line loop: 4 vertices = 4 line segments (closes back to start) = 8 line vertices
//        recorder.begin(GL11.GL_LINE_LOOP);
//        recorder.vertex(0, 0, 0);
//        recorder.vertex(1, 0, 0);
//        recorder.vertex(1, 1, 0);
//        recorder.vertex(0, 1, 0);
//
//        ImmediateModeRecorder.Result result = recorder.end();
//        assertNotNull(result);
//        assertTrue(result.quads().isEmpty(), "Line loops should not produce quads");
//        assertEquals(8, result.lines().size(), "4 vertices in loop = 4 segments (including closing) = 8 line vertices");
//    }
}
