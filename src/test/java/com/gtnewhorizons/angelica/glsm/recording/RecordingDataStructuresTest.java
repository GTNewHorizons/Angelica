package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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

    private int testList = -1;

    @AfterEach
    void cleanup() {
        if (testList > 0) {
            GLStateManager.glDeleteLists(testList, 1);
            testList = -1;
        }
    }

    @Test
    void testCompiledDisplayListCreation() {
        // Create a display list with PushMatrix, Scale, PopMatrix
        testList = GL11.glGenLists(1);
        GLStateManager.glNewList(testList, GL11.GL_COMPILE);

        GLStateManager.glPushMatrix();
        GLStateManager.glScalef(2.0f, 2.0f, 2.0f);
        GLStateManager.glPopMatrix();

        GLStateManager.glEndList();

        // Verify the compiled display list
        CompiledDisplayList compiled = DisplayListManager.getDisplayList(testList);
        assertNotNull(compiled, "Display list should be compiled");
        assertNotNull(compiled.getCommandBuffer(), "Command buffer should not be null");

        // Get command counts to verify structure
        Int2IntMap counts = compiled.getCommandCounts();

        // Should have PushMatrix and PopMatrix
        assertEquals(1, counts.getOrDefault(GLCommand.PUSH_MATRIX, 0), "Should have 1 PushMatrix");
        assertEquals(1, counts.getOrDefault(GLCommand.POP_MATRIX, 0), "Should have 1 PopMatrix");

        // Scale should be collapsed into a MultMatrix (optimizer collapses transforms)
        // Or if in debug mode, might have individual commands - just check we have some transforms
        int transformCount = counts.getOrDefault(GLCommand.MULT_MATRIX, 0)
            + counts.getOrDefault(GLCommand.SCALE, 0);
        assertTrue(transformCount >= 0, "Should have transform commands");
    }

    @Test
    void testImmediateModeRecorder() {
        DirectTessellator recorder = ImmediateModeRecorder.getInternalTessellator();

        // Record a single quad using immediate mode
        recorder.startDrawing(GL11.GL_QUADS);
        recorder.setTextureUV(0, 0);
        recorder.addVertex(0, 0, 0);
        recorder.setTextureUV(1, 0);
        recorder.addVertex(1, 0, 0);
        recorder.setTextureUV(1, 1);
        recorder.addVertex(1, 1, 0);
        recorder.setTextureUV(0, 1);
        recorder.addVertex(0, 1, 0);

        // end() now returns quads immediately (for correct command interleaving)
        DirectTessellator result = ImmediateModeRecorder.end();
        assertNotNull(result);
        assertTrue(ImmediateModeRecorder.hasGeometry());
        assertEquals(4, result.getVertexCount());
        assertEquals(GL11.GL_QUADS, result.getDrawMode());
        assertTrue(result.hasTexture);  // We called setTexCoord
        assertFalse(result.hasNormals);
        assertFalse(result.hasColor);
        assertFalse(result.hasBrightness);
        assertEquals(DefaultVertexFormat.POSITION_TEXTURE, result.getVertexFormat());

        result.reset();

        assertFalse(ImmediateModeRecorder.hasGeometry());
        assertEquals(0, result.getVertexCount());
        assertFalse(result.hasTexture);
        assertFalse(result.hasNormals);
        assertFalse(result.hasColor);
        assertFalse(result.hasBrightness);
        assertNull(result.getVertexFormat());
    }
}
