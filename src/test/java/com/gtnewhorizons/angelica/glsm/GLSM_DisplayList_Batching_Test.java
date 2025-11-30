package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.DrawVBOCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.MultMatrixCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.TranslateCmd;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizons.angelica.glsm.DisplayListTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for display list batching behavior.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Consecutive draws with same flags are batched into single VBOs</li>
 *   <li>Transform changes break batches</li>
 *   <li>Different flags break batches</li>
 * </ul>
 *
 * <p>Tests call {@link DisplayListManager#buildOptimizedDisplayList} directly
 * with manually constructed AccumulatedDraws. GL context needed for VBO creation.</p>
 */
@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_Batching_Test {

    // ==================== Helper Methods ====================

    private long countDrawVBOCmds(List<DisplayListCommand> commands) {
        return commands.stream().filter(cmd -> cmd instanceof DrawVBOCmd).count();
    }

    private long countMultMatrixCmds(List<DisplayListCommand> commands) {
        return commands.stream().filter(cmd -> cmd instanceof MultMatrixCmd).count();
    }

    // ==================== Pure Unit Tests (No GL Context) ====================

    @Test
    void testConsecutiveDrawsWithSameFlagsAreBatched() {
        // Setup: 3 draws at the same command index (consecutive), same flags
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createSimpleDraw(0));
        draws.add(createSimpleDraw(0));
        draws.add(createSimpleDraw(0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: should batch into 1 DrawVBOCmd
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(1, drawCount,
            "3 consecutive draws with same flags should batch into 1 DrawVBOCmd");
    }

    @Test
    void testDrawsWithDifferentFlagsAreNotBatched() {
        // Setup: 3 draws with different flags (using formats with quad writers)
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithFlags(positionOnly(), 0));
        draws.add(createDrawWithFlags(positionTexture(), 0));
        draws.add(createDrawWithFlags(positionColorTexture(), 0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: should NOT batch - different flags
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(3, drawCount,
            "3 draws with different flags should produce 3 DrawVBOCmds");
    }

    @Test
    void testTransformChangeBreaksBatch() {
        // Setup: draws with different transforms (simulating what happens during actual recording)
        // When recording, draws capture the current transform state at the moment they're recorded.
        // Draws with different transforms cannot be batched together.
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithTransform(new Matrix4f(), 0));  // Identity transform
        draws.add(createDrawWithTransform(new Matrix4f().translate(1.0f, 0.0f, 0.0f), 2));  // After translate
        draws.add(createDrawWithTransform(new Matrix4f().translate(3.0f, 0.0f, 0.0f), 4));  // After more translates

        List<DisplayListCommand> commands = new ArrayList<>();
        commands.add(new TranslateCmd(1.0, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 0
        commands.add(new TranslateCmd(2.0, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 1
        commands.add(new TranslateCmd(3.0, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 2
        commands.add(new TranslateCmd(4.0, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 3

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: different transforms should break batching
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(3, drawCount,
            "Draws with different transforms should produce 3 DrawVBOCmds");

        // Should have MultMatrix commands for the transforms
        long matrixCount = countMultMatrixCmds(optimized);
        assertTrue(matrixCount >= 2,
            "Should have MultMatrix commands for each unique transform");
    }

    @Test
    void testManyConsecutiveDrawsBatchEfficiently() {
        // Setup: 100 draws at same position
        List<AccumulatedDraw> draws = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            draws.add(createSimpleDraw(0));
        }

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: all should batch into 1
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(1, drawCount,
            "100 consecutive draws should batch into 1 DrawVBOCmd");
    }

    @Test
    void testDrawsWithMultipleQuadsBatch() {
        // Setup: draws with varying quad counts should still batch if same flags
        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithQuads(1, 0));
        draws.add(createDrawWithQuads(5, 0));
        draws.add(createDrawWithQuads(10, 0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: should batch into 1 (total 16 quads)
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(1, drawCount,
            "Draws with different quad counts but same flags should batch");
    }

    @Test
    void testEmptyDrawListProducesNoDrawCommands() {
        // Setup: no draws
        List<AccumulatedDraw> draws = new ArrayList<>();
        List<DisplayListCommand> commands = new ArrayList<>();
        commands.add(new TranslateCmd(1.0, 0.0, 0.0, GL11.GL_MODELVIEW));

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: no DrawVBOCmds
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(0, drawCount,
            "No draws should produce no DrawVBOCmds");

        // But should still have the collapsed transform
        long matrixCount = countMultMatrixCmds(optimized);
        assertEquals(1, matrixCount,
            "Transform should still be emitted even without draws");
    }

    @Test
    void testCancellingTransformsStillBreakBatch() {
        // This tests the specific bug where transforms cancel out in the command stream
        // (e.g., translate(+0.5) then translate(-0.5)) but draws captured in between
        // have different transform states that must be preserved.
        //
        // Before the fix, hasPendingTransform() would return FALSE because the accumulated
        // transform equals the lastEmitted transform (both identity), causing draws with
        // different recorded transforms to be incorrectly batched together.

        List<AccumulatedDraw> draws = new ArrayList<>();
        // Draw 1: captured at identity
        draws.add(createDrawWithTransform(new Matrix4f(), 0));
        // Draw 2: captured at translate(0.5, 0, 0) - BEFORE the cancelling translate(-0.5)
        draws.add(createDrawWithTransform(new Matrix4f().translate(0.5f, 0.0f, 0.0f), 2));
        // Draw 3: captured back at identity (after translate(-0.5) cancelled out)
        draws.add(createDrawWithTransform(new Matrix4f(), 4));

        List<DisplayListCommand> commands = new ArrayList<>();
        commands.add(new TranslateCmd(0.5, 0.0, 0.0, GL11.GL_MODELVIEW));   // index 0: move +0.5
        commands.add(new TranslateCmd(-0.5, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 1: move -0.5 (cancels out)
        commands.add(new TranslateCmd(0.5, 0.0, 0.0, GL11.GL_MODELVIEW));   // index 2: move +0.5
        commands.add(new TranslateCmd(-0.5, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 3: move -0.5 (cancels out)

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: even though command stream transforms cancel out, draws 1 and 2 have different
        // recorded transforms and MUST NOT be batched together
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(3, drawCount,
            "Draws with different recorded transforms must not batch, even if command stream transforms cancel out");

        // Verify that draws 1 and 3 could theoretically batch (same transform) but don't
        // because draw 2 with different transform is in between
        // This is correct behavior - we emit in order
    }

    @Test
    void testDrawsWithSameTransformBatch() {
        // Draws with the same transform CAN be batched together
        Matrix4f commonTransform = new Matrix4f().translate(1.0f, 2.0f, 3.0f);

        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0));
        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0));
        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: all draws have same transform, should batch into 1
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(1, drawCount,
            "Draws with same transform should batch into 1 DrawVBOCmd");

        // Should have 1 MultMatrix for the common transform
        long matrixCount = countMultMatrixCmds(optimized);
        assertEquals(1, matrixCount,
            "Should have exactly 1 MultMatrix for the common transform");
    }

    @Test
    void testInterleavedFlagsBreakAndRestartBatches() {
        // Setup: A A B A A - should produce 3 batches (2 A's, 1 B, 2 A's)
        // Use formats with quad writers: positionOnly vs positionTexture
        CapturingTessellator.Flags flagsA = positionOnly();
        CapturingTessellator.Flags flagsB = positionTexture();

        List<AccumulatedDraw> draws = new ArrayList<>();
        draws.add(createDrawWithFlags(flagsA, 0));  // Batch 1
        draws.add(createDrawWithFlags(flagsA, 0));  // Batch 1
        draws.add(createDrawWithFlags(flagsB, 0));  // Batch 2 (different flags)
        draws.add(createDrawWithFlags(flagsA, 0));  // Batch 3 (back to A, but new batch)
        draws.add(createDrawWithFlags(flagsA, 0));  // Batch 3

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        List<DisplayListCommand> optimized = DisplayListManager.buildOptimizedDisplayList(
            commands, draws, 1);

        // Verify: 3 batches
        long drawCount = countDrawVBOCmds(optimized);
        assertEquals(3, drawCount,
            "A A B A A pattern should produce 3 DrawVBOCmds");
    }
}
