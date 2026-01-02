package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.DrawRangeCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.MultMatrixCmd;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 *
 * <p>Tests call {@link DisplayListManager#buildOptimizedDisplayList} directly
 * with manually constructed AccumulatedDraws. GL context needed for VBO creation.</p>
 */
@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_Batching_Test {

    // ==================== Helper Methods ====================

    private long countDrawRangeCmds(DisplayListCommand[] commands) {
        long count = 0;
        for (DisplayListCommand cmd : commands) {
            if (cmd instanceof DrawRangeCmd) count++;
        }
        return count;
    }

    private long countMultMatrixCmds(DisplayListCommand[] commands) {
        long count = 0;
        for (DisplayListCommand cmd : commands) {
            if (cmd instanceof MultMatrixCmd) count++;
        }
        return count;
    }

    private Set<VertexBuffer> getUniqueVBOs(DisplayListCommand[] commands) {
        Set<VertexBuffer> vbos = new HashSet<>();
        for (DisplayListCommand cmd : commands) {
            if (cmd instanceof DrawRangeCmd rangeCmd)  {
                vbos.add(rangeCmd.sharedVbo());
            }
        }
        return vbos;
    }

    // ==================== Pure Unit Tests (No GL Context) ====================

    @Test
    void testConsecutiveDrawsWithSameFormatShareSingleVBO() {
        // Setup: 3 draws at the same command index, same format
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createSimpleDraw(0));
        draws.add(createSimpleDraw(0));
        draws.add(createSimpleDraw(0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 VBO for all draws (format-based batching)
        assertEquals(1, result.ownedVbos().length,
            "3 draws with same format should produce 1 shared VBO");

        // Verify: 1 DrawRangeCmd covering all geometry
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(1, drawCount,
            "3 consecutive draws with same format and transform should produce 1 DrawRangeCmd");
    }

    @Test
    void testDrawsWithDifferentFormatsProduceSeparateVBOs() {
        // Setup: 3 draws with different formats
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithFlags(positionOnly(), 0));
        draws.add(createDrawWithFlags(positionTexture(), 0));
        draws.add(createDrawWithFlags(positionColorTexture(), 0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 3 VBOs (one per format)
        assertEquals(3, result.ownedVbos().length,
            "3 draws with different formats should produce 3 VBOs");

        // Verify: 3 DrawRangeCmds
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(3, drawCount, "3 draws with different formats should produce 3 DrawRangeCmds");
    }

    @Test
    void testDifferentTransformsSameFormatShareVBO() {
        // Setup: draws with different transforms but same format
        // With format-based batching, they share 1 VBO but produce separate DrawRangeCmds
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithTransform(new Matrix4f(), 0, 0));  // Identity transform, gen=0
        draws.add(createDrawWithTransform(new Matrix4f().translate(1.0f, 0.0f, 0.0f), 1, 1));  // T(1), gen=1
        draws.add(createDrawWithTransform(new Matrix4f().translate(3.0f, 0.0f, 0.0f), 2, 2));  // T(3), gen=2

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 shared VBO (same format)
        assertEquals(1, result.ownedVbos().length, "Draws with different transforms but same format should share 1 VBO");

        // Verify: 3 DrawRangeCmds (one per draw, each with different transform)
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(3, drawCount, "3 draws with different transforms should produce 3 DrawRangeCmds");

        // Verify: All DrawRangeCmds reference the same VBO
        Set<VertexBuffer> uniqueVBOs = getUniqueVBOs(optimized);
        assertEquals(1, uniqueVBOs.size(), "All DrawRangeCmds should reference the same shared VBO");

        // Should have MultMatrix commands for the transforms (2 for T(1) and T(3))
        long matrixCount = countMultMatrixCmds(optimized);
        assertEquals(2, matrixCount, "Should have MultMatrix commands for each unique transform");
    }

    @Test
    void testManyDrawsSameFormatProduceSingleVBO() {
        // Setup: 100 draws at same position, same format
        List<AccumulatedDraw> draws = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            draws.add(createSimpleDraw(0));
        }

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 shared VBO
        assertEquals(1, result.ownedVbos().length, "100 draws with same format should produce 1 shared VBO");

        // Verify: 1 DrawRangeCmd (all same transform)
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(1, drawCount,
            "100 consecutive draws with same format and transform should produce 1 DrawRangeCmd");
    }

    @Test
    void testDrawsWithMultipleQuadsSameFormat() {
        // Setup: draws with varying quad counts should share VBO if same format
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithQuads(1, 0));
        draws.add(createDrawWithQuads(5, 0));
        draws.add(createDrawWithQuads(10, 0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 shared VBO (total 16 quads)
        assertEquals(1, result.ownedVbos().length, "Draws with different quad counts but same format should share 1 VBO");

        // Verify: 1 DrawRangeCmd (all same transform)
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(1, drawCount, "Draws with different quad counts but same format and transform should produce 1 DrawRangeCmd");
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

        List<AccumulatedDraw> draws = new ArrayList<>();
        // Draw 1: captured at identity, gen=0
        draws.add(createDrawWithTransform(new Matrix4f(), 0, 0));
        // Draw 2: captured at translate(0.5, 0, 0), gen=1 (transform changed)
        draws.add(createDrawWithTransform(new Matrix4f().translate(0.5f, 0.0f, 0.0f), 1, 1));
        // Draw 3: captured back at identity, gen=2 (transform changed again)
        draws.add(createDrawWithTransform(new Matrix4f(), 2, 2));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 shared VBO (same format)
        assertEquals(1, result.ownedVbos().length, "Draws with same format should share 1 VBO regardless of transforms");

        // Verify: 3 DrawRangeCmds (different transforms require separate draw commands)
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(3, drawCount, "Draws with different recorded transforms must produce separate DrawRangeCmds");
    }

    @Test
    void testDrawsWithSameTransformProduceSingleDrawRangeCmd() {
        // Draws with the same format AND same transform produce a single DrawRangeCmd
        Matrix4f commonTransform = new Matrix4f().translate(1.0f, 2.0f, 3.0f);

        List<AccumulatedDraw> draws = new ArrayList<>();
        // All same transform → same generation → will batch into single DrawRangeCmd
        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0, 1));
        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0, 1));
        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0, 1));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 shared VBO
        assertEquals(1, result.ownedVbos().length,
            "Draws with same format should share 1 VBO");

        // Verify: 1 DrawRangeCmd (same format AND same transform)
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(1, drawCount, "Draws with same format and transform should produce 1 DrawRangeCmd");

        // Should have 1 MultMatrix for the common transform
        long matrixCount = countMultMatrixCmds(optimized);
        assertEquals(1, matrixCount,
            "Should have exactly 1 MultMatrix for the common transform");
    }

    /**
     * Test that draws with same transform but different stateGeneration do NOT merge.
     * This simulates state commands (draw barriers) between draws.
     *
     * Scenario: Draw1, glColor(red), Draw2, glBindTexture, Draw3
     * All draws have same transform (gen=1) but different batches (0, 1, 2).
     * Expected: 3 separate DrawRangeCmds (no merging across draw barriers).
     *
     * This test will FAIL until FormatBuffer is fixed to check stateGeneration.
     */
    @Test
    void testDrawsWithDifferentBatchIdDoNotMerge() {
        // Setup: 3 draws with same transform generation but different batch IDs
        // This simulates state commands (draw barriers) between draws
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithTransformAndBatch(new Matrix4f(), 0, 1, 0));  // batch 0
        draws.add(createDrawWithTransformAndBatch(new Matrix4f(), 1, 1, 1));  // batch 1 (state cmd between)
        draws.add(createDrawWithTransformAndBatch(new Matrix4f(), 2, 1, 2));  // batch 2 (state cmd between)

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 shared VBO (same format)
        assertEquals(1, result.ownedVbos().length,
            "Draws with same format should share 1 VBO");

        // Verify: 3 DrawRangeCmds (NOT merged due to different batch IDs)
        // BUG: Currently this will produce 1 DrawRangeCmd because FormatBuffer
        // only checks matrixGeneration, not stateGeneration
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(3, drawCount,
            "Draws with same transform but different batch IDs (draw barriers between) must NOT merge");
    }

    @Test
    void testInterleavedFormatsProduceSeparateVBOs() {
        // Setup: A A B A A - should produce 2 VBOs (one for A, one for B)
        // With format-based batching, all A draws are grouped together and merged
        // (since they have same transform), producing 2 DrawRangeCmds total
        CapturingTessellator.Flags flagsA = positionOnly();
        CapturingTessellator.Flags flagsB = positionTexture();

        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithFlags(flagsA, 0));  // A
        draws.add(createDrawWithFlags(flagsA, 0));  // A
        draws.add(createDrawWithFlags(flagsB, 0));  // B
        draws.add(createDrawWithFlags(flagsA, 0));  // A
        draws.add(createDrawWithFlags(flagsA, 0));  // A

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 2 VBOs (one for format A, one for format B)
        assertEquals(2, result.ownedVbos().length, "A A B A A pattern should produce 2 VBOs (one per format)");

        // Verify: 2 DrawRangeCmds (all A's merged into 1, B is 1)
        // With format-based batching, all A draws go into the same FormatBuffer
        // and get merged (same transform). Order within same command index doesn't matter.
        long drawCount = countDrawRangeCmds(optimized);
        assertEquals(2, drawCount, "A A B A A pattern with same transform should produce 2 DrawRangeCmds (A's merged, B separate)");
    }
}
