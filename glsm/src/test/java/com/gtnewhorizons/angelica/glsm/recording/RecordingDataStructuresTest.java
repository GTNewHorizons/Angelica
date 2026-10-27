package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.GLCompatTest;
import com.gtnewhorizons.angelica.glsm.GLCompatTest;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for display list recording data structures.
 */
@GLCompatTest
class RecordingDataStructuresTest {

    private int testList = -1;

    @AfterEach
    void cleanup() {
        if (testList > 0) {
            GLSMDraws.glDeleteLists(testList, 1);
            testList = -1;
        }
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
