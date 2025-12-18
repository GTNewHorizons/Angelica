package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.DrawRangeCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.MultMatrixCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.TranslateCmd;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

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

//    // ==================== Helper Methods ====================
//
//    private long countDrawRangeCmds(DisplayListCommand[] commands) {
//        long count = 0;
//        for (DisplayListCommand cmd : commands) {
//            if (cmd instanceof DrawRangeCmd) count++;
//        }
//        return count;
//    }
//
//    private long countMultMatrixCmds(DisplayListCommand[] commands) {
//        long count = 0;
//        for (DisplayListCommand cmd : commands) {
//            if (cmd instanceof MultMatrixCmd) count++;
//        }
//        return count;
//    }
//
//    private Set<VertexBuffer> getUniqueVBOs(DisplayListCommand[] commands) {
//        Set<VertexBuffer> vbos = new HashSet<>();
//        for (DisplayListCommand cmd : commands) {
//            if (cmd instanceof DrawRangeCmd rangeCmd)  {
//                vbos.add(rangeCmd.sharedVbo());
//            }
//        }
//        return vbos;
//    }
//
//    // ==================== Pure Unit Tests (No GL Context) ====================
//
//    @Test
//    void testConsecutiveDrawsWithSameFormatShareSingleVBO() {
//        // Setup: 3 draws at the same command index, same format
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        draws.add(createSimpleDraw(0));
//        draws.add(createSimpleDraw(0));
//        draws.add(createSimpleDraw(0));
//
//        List<DisplayListCommand> commands = new ArrayList<>();
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(
//            commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: 1 VBO for all draws (format-based batching)
//        assertEquals(1, result.ownedVbos().length,
//            "3 draws with same format should produce 1 shared VBO");
//
//        // Verify: 1 DrawRangeCmd covering all geometry
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(1, drawCount,
//            "3 consecutive draws with same format and transform should produce 1 DrawRangeCmd");
//    }
//
//    @Test
//    void testDrawsWithDifferentFormatsProduceSeparateVBOs() {
//        // Setup: 3 draws with different formats
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        draws.add(createDrawWithFlags(positionOnly(), 0));
//        draws.add(createDrawWithFlags(positionTexture(), 0));
//        draws.add(createDrawWithFlags(positionColorTexture(), 0));
//
//        List<DisplayListCommand> commands = new ArrayList<>();
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: 3 VBOs (one per format)
//        assertEquals(3, result.ownedVbos().length,
//            "3 draws with different formats should produce 3 VBOs");
//
//        // Verify: 3 DrawRangeCmds
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(3, drawCount, "3 draws with different formats should produce 3 DrawRangeCmds");
//    }
//
//    @Test
//    void testDifferentTransformsSameFormatShareVBO() {
//        // Setup: draws with different transforms but same format
//        // With format-based batching, they share 1 VBO but produce separate DrawRangeCmds
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        draws.add(createDrawWithTransform(new Matrix4f(), 0));  // Identity transform
//        draws.add(createDrawWithTransform(new Matrix4f().translate(1.0f, 0.0f, 0.0f), 2));  // After translate
//        draws.add(createDrawWithTransform(new Matrix4f().translate(3.0f, 0.0f, 0.0f), 4));  // After more translates
//
//        List<DisplayListCommand> commands = new ArrayList<>();
//        commands.add(new TranslateCmd(1.0, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 0
//        commands.add(new TranslateCmd(2.0, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 1
//        commands.add(new TranslateCmd(3.0, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 2
//        commands.add(new TranslateCmd(4.0, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 3
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: 1 shared VBO (same format)
//        assertEquals(1, result.ownedVbos().length, "Draws with different transforms but same format should share 1 VBO");
//
//        // Verify: 3 DrawRangeCmds (one per draw, each with different transform)
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(3, drawCount, "3 draws with different transforms should produce 3 DrawRangeCmds");
//
//        // Verify: All DrawRangeCmds reference the same VBO
//        Set<VertexBuffer> uniqueVBOs = getUniqueVBOs(optimized);
//        assertEquals(1, uniqueVBOs.size(), "All DrawRangeCmds should reference the same shared VBO");
//
//        // Should have MultMatrix commands for the transforms
//        long matrixCount = countMultMatrixCmds(optimized);
//        assertTrue(matrixCount >= 2, "Should have MultMatrix commands for each unique transform");
//    }
//
//    @Test
//    void testManyDrawsSameFormatProduceSingleVBO() {
//        // Setup: 100 draws at same position, same format
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        for (int i = 0; i < 100; i++) {
//            draws.add(createSimpleDraw(0));
//        }
//
//        List<DisplayListCommand> commands = new ArrayList<>();
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(
//            commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: 1 shared VBO
//        assertEquals(1, result.ownedVbos().length, "100 draws with same format should produce 1 shared VBO");
//
//        // Verify: 1 DrawRangeCmd (all same transform)
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(1, drawCount,
//            "100 consecutive draws with same format and transform should produce 1 DrawRangeCmd");
//    }
//
//    @Test
//    void testDrawsWithMultipleQuadsSameFormat() {
//        // Setup: draws with varying quad counts should share VBO if same format
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        draws.add(createDrawWithQuads(1, 0));
//        draws.add(createDrawWithQuads(5, 0));
//        draws.add(createDrawWithQuads(10, 0));
//
//        List<DisplayListCommand> commands = new ArrayList<>();
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: 1 shared VBO (total 16 quads)
//        assertEquals(1, result.ownedVbos().length, "Draws with different quad counts but same format should share 1 VBO");
//
//        // Verify: 1 DrawRangeCmd (all same transform)
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(1, drawCount, "Draws with different quad counts but same format and transform should produce 1 DrawRangeCmd");
//    }
//
//    @Test
//    void testEmptyDrawListProducesNoVBOs() {
//        // Setup: no draws
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        List<DisplayListCommand> commands = new ArrayList<>();
//        commands.add(new TranslateCmd(1.0, 0.0, 0.0, GL11.GL_MODELVIEW));
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(
//            commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: no VBOs
//        assertEquals(0, result.ownedVbos().length, "No draws should produce no VBOs");
//
//        // Verify: no DrawRangeCmds
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(0, drawCount, "No draws should produce no DrawRangeCmds");
//
//        // But should still have the collapsed transform
//        long matrixCount = countMultMatrixCmds(optimized);
//        assertEquals(1, matrixCount,
//            "Transform should still be emitted even without draws");
//    }
//
//    @Test
//    void testCancellingTransformsProduceSeparateDrawRangeCmds() {
//        // This tests the specific bug where transforms cancel out in the command stream
//        // (e.g., translate(+0.5) then translate(-0.5)) but draws captured in between
//        // have different transform states that must be preserved.
//        //
//        // With format-based batching: 1 shared VBO, 3 DrawRangeCmds
//
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        // Draw 1: captured at identity
//        draws.add(createDrawWithTransform(new Matrix4f(), 0));
//        // Draw 2: captured at translate(0.5, 0, 0) - BEFORE the cancelling translate(-0.5)
//        draws.add(createDrawWithTransform(new Matrix4f().translate(0.5f, 0.0f, 0.0f), 2));
//        // Draw 3: captured back at identity (after translate(-0.5) cancelled out)
//        draws.add(createDrawWithTransform(new Matrix4f(), 4));
//
//        List<DisplayListCommand> commands = new ArrayList<>();
//        commands.add(new TranslateCmd(0.5, 0.0, 0.0, GL11.GL_MODELVIEW));   // index 0: move +0.5
//        commands.add(new TranslateCmd(-0.5, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 1: move -0.5 (cancels out)
//        commands.add(new TranslateCmd(0.5, 0.0, 0.0, GL11.GL_MODELVIEW));   // index 2: move +0.5
//        commands.add(new TranslateCmd(-0.5, 0.0, 0.0, GL11.GL_MODELVIEW));  // index 3: move -0.5 (cancels out)
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(
//            commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: 1 shared VBO (same format)
//        assertEquals(1, result.ownedVbos().length, "Draws with same format should share 1 VBO regardless of transforms");
//
//        // Verify: 3 DrawRangeCmds (different transforms require separate draw commands)
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(3, drawCount, "Draws with different recorded transforms must produce separate DrawRangeCmds");
//    }
//
//    @Test
//    void testDrawsWithSameTransformProduceSingleDrawRangeCmd() {
//        // Draws with the same format AND same transform produce a single DrawRangeCmd
//        Matrix4f commonTransform = new Matrix4f().translate(1.0f, 2.0f, 3.0f);
//
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0));
//        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0));
//        draws.add(createDrawWithTransform(new Matrix4f(commonTransform), 0));
//
//        List<DisplayListCommand> commands = new ArrayList<>();
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: 1 shared VBO
//        assertEquals(1, result.ownedVbos().length,
//            "Draws with same format should share 1 VBO");
//
//        // Verify: 1 DrawRangeCmd (same format AND same transform)
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(1, drawCount, "Draws with same format and transform should produce 1 DrawRangeCmd");
//
//        // Should have 1 MultMatrix for the common transform
//        long matrixCount = countMultMatrixCmds(optimized);
//        assertEquals(1, matrixCount,
//            "Should have exactly 1 MultMatrix for the common transform");
//    }
//
//    @Test
//    void testInterleavedFormatsProduceSeparateVBOs() {
//        // Setup: A A B A A - should produce 2 VBOs (one for A, one for B)
//        // With format-based batching, all A draws are grouped together and merged
//        // (since they have same transform), producing 2 DrawRangeCmds total
//        CapturingTessellator.Flags flagsA = positionOnly();
//        CapturingTessellator.Flags flagsB = positionTexture();
//
//        List<AccumulatedDraw> draws = new ArrayList<>();
//        draws.add(createDrawWithFlags(flagsA, 0));  // A
//        draws.add(createDrawWithFlags(flagsA, 0));  // A
//        draws.add(createDrawWithFlags(flagsB, 0));  // B
//        draws.add(createDrawWithFlags(flagsA, 0));  // A
//        draws.add(createDrawWithFlags(flagsA, 0));  // A
//
//        List<DisplayListCommand> commands = new ArrayList<>();
//
//        // Execute
//        OptimizedListResult result = DisplayListTestHelper.buildOptimizedDisplayList(
//            commands, draws, null, 1);
//        DisplayListCommand[] optimized = result.commands();
//
//        // Verify: 2 VBOs (one for format A, one for format B)
//        assertEquals(2, result.ownedVbos().length, "A A B A A pattern should produce 2 VBOs (one per format)");
//
//        // Verify: 2 DrawRangeCmds (all A's merged into 1, B is 1)
//        // With format-based batching, all A draws go into the same FormatBuffer
//        // and get merged (same transform). Order within same command index doesn't matter.
//        long drawCount = countDrawRangeCmds(optimized);
//        assertEquals(2, drawCount, "A A B A A pattern with same transform should produce 2 DrawRangeCmds (A's merged, B separate)");
//    }
}
