package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.vbo.IVertexBuffer;
import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.DisplayListVBO;
import com.gtnewhorizons.angelica.glsm.recording.GLCommand;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.Set;

import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for display list format-based batching behavior.
 *
 * <p>With format-based batching, VBO allocation depends ONLY on vertex format:
 * <ul>
 *   <li>All draws with the same format share a single VBO</li>
 *   <li>Different transforms produce separate DrawRangeCmd but share the VBO</li>
 *   <li>Different formats produce separate VBOs</li>
 * </ul>
 */
@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_Batching_Test {

    private static int getVBOCount(CompiledDisplayList compiledDisplayList) {
        Set<IVertexBuffer> vaos = new HashSet<>();
        for (DisplayListVBO.SubVBO vbo : compiledDisplayList.getOwnedVbos().getVBOs()) {
            vaos.add(vbo.getVAO().getVBO());
        }
        return vaos.size();
    }

    // ==================== Pure Unit Tests (No GL Context) ====================

    @Test
    void testConsecutiveDrawsWithSameFormatShareSingleVBO() {
        CompiledDisplayList compiled = createDisplayList(() -> {
            createSimpleDraw();
            createSimpleDraw();
            createSimpleDraw();
        });


        // Verify: 1 VBO for all draws (format-based batching)
        assertEquals(1, getVBOCount(compiled),
            "3 draws with same format should produce 1 shared VBO");

        // Verify: 1 DrawRangeCmd covering all geometry
        Int2IntMap counts = compiled.getCommandCounts();
        int drawCount = counts.getOrDefault(GLCommand.DRAW_RANGE, 0);
        assertEquals(1, drawCount,
            "3 consecutive draws with same format and transform should produce 1 DrawRangeCmd");


    }

    @Test
    void testDrawsWithDifferentFormatsProduceSeparateVBOs() {
        CompiledDisplayList compiled = createDisplayList(() -> {
            createSimpleDraw();
            createTextureDraw();
            createColorDraw();
        });
        // Verify: 3 VBOs (one per format)
        assertEquals(3, getVBOCount(compiled),
            "3 draws with different formats should produce 3 VBOs");

        // Verify: 3 DrawRangeCmds
        Int2IntMap counts = compiled.getCommandCounts();
        int drawCount = counts.getOrDefault(GLCommand.DRAW_RANGE, 0);
        int drawRestoreCount = counts.getOrDefault(GLCommand.DRAW_RANGE_RESTORE, 0);
        assertEquals(1, drawCount, "3 draws with different formats should produce 3 DrawRangeCmds");
        assertEquals(2, drawRestoreCount, "3 draws with different formats should produce 3 DrawRangeCmds");
    }

    @Test
    void testDifferentTransformsSameFormatShareVBO() {
        CompiledDisplayList compiled = createDisplayList(() -> {
            createSimpleDraw();
            GLStateManager.glTranslatef(1, 0, 0);
            createSimpleDraw();
            GLStateManager.glTranslatef(2, 0, 0);
            createSimpleDraw();
        });

        // Verify: 1 shared VBO (same format)
        assertEquals(1, getVBOCount(compiled), "Draws with different transforms but same format should share 1 VBO");

        // Verify: 3 DrawRangeCmds (one per draw, each with different transform)
        Int2IntMap counts = compiled.getCommandCounts();
        int drawCount = counts.getOrDefault(GLCommand.DRAW_RANGE, 0);
        int multMatrixCount = counts.getOrDefault(GLCommand.MULT_MATRIX, 0);
        assertEquals(3, drawCount, "3 draws with different transforms should produce 3 DrawRangeCmds");
        assertEquals(2, multMatrixCount, "Should have MultMatrix commands for each unique transform");
    }

    @Test
    void testManyDrawsSameFormatProduceSingleVBO() {
        CompiledDisplayList compiled = createDisplayList(() -> {
            for (int i = 0; i < 100; i++) {
                createSimpleDraw();
            }
        });

        // Verify: 1 shared VBO
        assertEquals(1, getVBOCount(compiled), "100 draws with same format should produce 1 shared VBO");

        Int2IntMap counts = compiled.getCommandCounts();
        int drawCount = counts.getOrDefault(GLCommand.DRAW_RANGE, 0);
        assertEquals(1, drawCount,
            "100 consecutive draws with same format and transform should produce 1 DrawRangeCmd");
    }


    @Test
    void testCancellingTransformsProduceSeparateDrawRangeCmds() {
        // This tests the specific bug where transforms cancel out over the sequence
        // (e.g., translate(+0.5) then translate(-0.5)) but draws captured in between
        // have different transform states that must be preserved.
        //
        // With format-based batching: 1 shared VBO, 3 DrawRangeCmds
        // The key point: draws 1 and 3 have the same transform (identity) but draw 2
        // has a different transform, so they can't all be merged.
        CompiledDisplayList compiled = createDisplayList(() -> {
            createSimpleDraw();
            GLStateManager.glTranslatef(0.5f, 0, 0);
            createSimpleDraw();
            GLStateManager.glTranslatef(-0.5f, 0, 0);
            createSimpleDraw();
        });

        // Verify: 1 shared VBO (same format)
        assertEquals(1, getVBOCount(compiled), "Draws with same format should share 1 VBO regardless of transforms");

        // Verify: 3 DrawRangeCmds (different transforms require separate draw commands)
        Int2IntMap counts = compiled.getCommandCounts();
        int drawCount = counts.getOrDefault(GLCommand.DRAW_RANGE, 0);
        assertEquals(3, drawCount, "Draws with different recorded transforms must produce separate DrawRangeCmds");
    }

    @Test
    void testDrawsWithSameTransformProduceSingleDrawRangeCmd() {
        CompiledDisplayList compiled = createDisplayList(() -> {
            GLStateManager.glTranslatef(1.0f, 2.0f, 3.0f);
            createSimpleDraw();
            createSimpleDraw();
            createSimpleDraw();
        });

        // Verify: 1 shared VBO
        assertEquals(1, getVBOCount(compiled), "Draws with same format should share 1 VBO");

        Int2IntMap counts = compiled.getCommandCounts();
        int drawCount = counts.getOrDefault(GLCommand.DRAW_RANGE, 0);
        int multMatrixCount = counts.getOrDefault(GLCommand.MULT_MATRIX, 0);
        assertEquals(1, drawCount, "Draws with same format and transform should produce 1 DrawRangeCmd");
        assertEquals(1, multMatrixCount, "Should have exactly 1 MultMatrix for the common transform");
    }

    /**
     * Test that draws with same transform but different stateGeneration do NOT merge.
     * This simulates state commands (draw barriers) between draws.
     * <br>
     * Scenario: Draw1, glColor(red), Draw2, glBindTexture, Draw3, Draw4
     * All draws have same transform (gen=1) but different batches (0, 1, 2).
     * Expected: 3 separate DrawRangeCmds (only draw 3 & 4 should get merged).
     * <br>
     * This test will FAIL until FormatBuffer is fixed to check stateGeneration.
     */
    @Test
    void testDrawsWithDifferentBatchIdDoNotMerge() {
        CompiledDisplayList compiled = createDisplayList(() -> {
            createSimpleDraw();
            DisplayListManager.drawBarrier();
            createSimpleDraw();
            DisplayListManager.drawBarrier();
            createSimpleDraw();
            createSimpleDraw();
        });

        // Verify: 1 shared VBO (same format)
        assertEquals(1, getVBOCount(compiled), "Draws with same format should share 1 VBO");

        // Verify: 3 DrawRangeCmds (NOT merged due to different batch IDs)
        Int2IntMap counts = compiled.getCommandCounts();
        int drawCount = counts.getOrDefault(GLCommand.DRAW_RANGE, 0);
        assertEquals(3, drawCount,
            "Draws with same transform but different batch IDs (draw barriers between) must NOT merge");
    }

    @Test
    void testInterleavedFormatsProduceSeparateVBOs() {
        CompiledDisplayList compiled = createDisplayList(() -> {
            createSimpleDraw();  // A
            createSimpleDraw();  // A
            createTextureDraw(); // B
            createSimpleDraw();  // A
            createSimpleDraw();  // A
        });
        Int2IntMap counts = compiled.getCommandCounts();
        int drawCount = counts.getOrDefault(GLCommand.DRAW_RANGE, 0);
        int drawRestoreCount = counts.getOrDefault(GLCommand.DRAW_RANGE_RESTORE, 0);

        // Verify: 2 VBOs (one for format A, one for format B)
        assertEquals(2, getVBOCount(compiled), "A A B A A pattern should produce 2 VBOs (one per format)");

        // Verify: 2 DrawRangeCmds (all A's merged into 1, B is 1)
        // With format-based batching, all A draws go into the same FormatBuffer
        // and get merged (same transform). Order within same command index doesn't matter.
        assertEquals(2, drawCount, "A A B A A pattern with same transform should produce 2 DrawRangeCmds (A's merged, B separate)");
        assertEquals(1, drawRestoreCount, "A A B A A pattern with same transform should produce 2 DrawRangeCmds (A's merged, B separate)");
    }
}
