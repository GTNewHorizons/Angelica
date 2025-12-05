package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.line.ModelLine;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.primitive.ModelPrimitiveView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.tri.ModelTriangle;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedPrimitiveDraw;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.DrawRangeCmd;
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
 * Tests for display list primitive (lines, triangles) handling.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>AccumulatedPrimitiveDraw deep copying behavior</li>
 *   <li>TessellatorPrimitiveBuffer accumulation and range merging</li>
 *   <li>Integration with buildOptimizedDisplayList for primitive VBOs</li>
 *   <li>Mixed quad + primitive display lists</li>
 * </ul>
 */
@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_Primitive_Test {

    // ==================== Helper Methods ====================

    private long countDrawRangeCmds(DisplayListCommand[] commands) {
        long count = 0;
        for (DisplayListCommand cmd : commands) {
            if (cmd instanceof DrawRangeCmd) count++;
        }
        return count;
    }

    private Set<VertexBuffer> getUniqueVBOs(DisplayListCommand[] commands) {
        Set<VertexBuffer> vbos = new HashSet<>();
        for (DisplayListCommand cmd : commands) {
            if (cmd instanceof DrawRangeCmd rangeCmd) {
                vbos.add(rangeCmd.sharedVbo());
            }
        }
        return vbos;
    }

    // ==================== AccumulatedPrimitiveDraw Tests ====================

    @Test
    void testAccumulatedPrimitiveDrawDeepCopiesLines() {
        // Setup: Create a line and capture its original values
        ModelLine originalLine = createSimpleLine();
        float originalX0 = originalLine.getX(0);
        float originalY0 = originalLine.getY(0);

        List<ModelPrimitiveView> primitives = new ArrayList<>();
        primitives.add(originalLine);

        // Execute: Create AccumulatedPrimitiveDraw (should deep copy)
        AccumulatedPrimitiveDraw draw = new AccumulatedPrimitiveDraw(
            primitives, identity(), positionOnly(), 0);

        // Modify original
        originalLine.setX(0, 999.0f);
        originalLine.setY(0, 888.0f);

        // Verify: Copied primitive should be unchanged
        ModelPrimitiveView copied = draw.primitives.get(0);
        assertTrue(copied instanceof ModelLine, "Copied primitive should be a ModelLine");
        ModelLine copiedLine = (ModelLine) copied;
        assertEquals(originalX0, copiedLine.getX(0), 0.001f, "Deep copy should preserve original X value");
        assertEquals(originalY0, copiedLine.getY(0), 0.001f, "Deep copy should preserve original Y value");
    }

    @Test
    void testAccumulatedPrimitiveDrawDeepCopiesTriangles() {
        // Setup: Create a triangle and capture its original values
        ModelTriangle originalTri = createSimpleTriangle();
        float originalX0 = originalTri.getX(0);
        float originalX1 = originalTri.getX(1);
        float originalX2 = originalTri.getX(2);

        List<ModelPrimitiveView> primitives = new ArrayList<>();
        primitives.add(originalTri);

        // Execute: Create AccumulatedPrimitiveDraw (should deep copy)
        AccumulatedPrimitiveDraw draw = new AccumulatedPrimitiveDraw(
            primitives, identity(), positionOnly(), 0);

        // Modify original
        originalTri.setX(0, 999.0f);
        originalTri.setX(1, 888.0f);
        originalTri.setX(2, 777.0f);

        // Verify: Copied primitive should be unchanged
        ModelPrimitiveView copied = draw.primitives.get(0);
        assertTrue(copied instanceof ModelTriangle, "Copied primitive should be a ModelTriangle");
        ModelTriangle copiedTri = (ModelTriangle) copied;
        assertEquals(originalX0, copiedTri.getX(0), 0.001f, "Deep copy should preserve original X0 value");
        assertEquals(originalX1, copiedTri.getX(1), 0.001f, "Deep copy should preserve original X1 value");
        assertEquals(originalX2, copiedTri.getX(2), 0.001f, "Deep copy should preserve original X2 value");
    }

    @Test
    void testAccumulatedPrimitiveDrawDeepCopiesMixedPrimitives() {
        // Setup: Create mixed primitives
        List<ModelPrimitiveView> primitives = createMixedPrimitives(2, 2);

        // Execute
        AccumulatedPrimitiveDraw draw = new AccumulatedPrimitiveDraw(
            primitives, identity(), positionOnly(), 0);

        // Verify: All primitives copied correctly
        assertEquals(4, draw.primitives.size(), "Should have 4 primitives (2 lines + 2 triangles)");

        int lineCount = 0;
        int triCount = 0;
        for (ModelPrimitiveView prim : draw.primitives) {
            if (prim instanceof ModelLine) lineCount++;
            else if (prim instanceof ModelTriangle) triCount++;
        }
        assertEquals(2, lineCount, "Should have 2 lines");
        assertEquals(2, triCount, "Should have 2 triangles");
    }

    @Test
    void testAccumulatedPrimitiveDrawDeepCopiesTransform() {
        // Setup
        Matrix4f originalTransform = translate(1.0f, 2.0f, 3.0f);
        List<ModelPrimitiveView> primitives = new ArrayList<>();
        primitives.add(createSimpleLine());

        // Execute
        AccumulatedPrimitiveDraw draw = new AccumulatedPrimitiveDraw(
            primitives, originalTransform, positionOnly(), 0);

        // Modify original transform
        originalTransform.identity();

        // Verify: Stored transform should be unchanged (not identity)
        assertFalse(draw.transform.equals(new Matrix4f()), "Transform should be deep copied, not identity");
        assertEquals(1.0f, draw.transform.m30(), 0.001f, "Transform X translation should be preserved");
        assertEquals(2.0f, draw.transform.m31(), 0.001f, "Transform Y translation should be preserved");
        assertEquals(3.0f, draw.transform.m32(), 0.001f, "Transform Z translation should be preserved");
    }

    // ==================== TessellatorPrimitiveBuffer Tests ====================

    @Test
    void testPrimitiveBufferSeparatesLinesByTransform() {
        // Setup: Create primitive buffer and add draws with different transforms
        TessellatorPrimitiveBuffer buffer = new TessellatorPrimitiveBuffer(positionOnly());

        AccumulatedPrimitiveDraw draw1 = createLinesDraw(2, 0);  // 2 lines at identity
        AccumulatedPrimitiveDraw draw2 = createPrimitiveDrawWithTransform(
            createMixedPrimitives(2, 0), translate(1, 0, 0), 1);  // 2 lines translated

        buffer.addDraw(draw1);
        buffer.addDraw(draw2);

        // Execute
        CompiledPrimitiveBuffers compiled = buffer.finish();

        // Verify: Has lines, no triangles
        assertNotNull(compiled, "Should compile non-empty buffer");
        assertTrue(compiled.hasLines(), "Should have lines");
        assertFalse(compiled.hasTriangles(), "Should not have triangles");

        // 2 merged ranges (different transforms)
        assertEquals(2, compiled.lineMergedRanges().length,
            "Different transforms should produce separate merged ranges");
    }

    @Test
    void testPrimitiveBufferMergesSameTransformLines() {
        // Setup: Multiple draws with same transform
        TessellatorPrimitiveBuffer buffer = new TessellatorPrimitiveBuffer(positionOnly());

        buffer.addDraw(createLinesDraw(2, 0));  // 2 lines
        buffer.addDraw(createLinesDraw(3, 0));  // 3 more lines, same transform
        buffer.addDraw(createLinesDraw(1, 0));  // 1 more line, same transform

        // Execute
        CompiledPrimitiveBuffers compiled = buffer.finish();

        // Verify: 1 merged range (all same transform)
        assertNotNull(compiled);
        assertEquals(1, compiled.lineMergedRanges().length,
            "Same transform draws should merge into 1 range");

        // Total vertices: (2+3+1) lines * 2 vertices = 12
        assertEquals(12, compiled.lineMergedRanges()[0].vertexCount(),
            "Merged range should contain all line vertices");
    }

    @Test
    void testPrimitiveBufferSeparatesTrianglesByTransform() {
        // Setup
        TessellatorPrimitiveBuffer buffer = new TessellatorPrimitiveBuffer(positionOnly());

        buffer.addDraw(createTrianglesDraw(2, 0));  // 2 triangles at identity
        buffer.addDraw(createPrimitiveDrawWithTransform(
            createMixedPrimitives(0, 2), translate(1, 0, 0), 1));  // 2 triangles translated

        // Execute
        CompiledPrimitiveBuffers compiled = buffer.finish();

        // Verify
        assertNotNull(compiled);
        assertFalse(compiled.hasLines(), "Should not have lines");
        assertTrue(compiled.hasTriangles(), "Should have triangles");

        assertEquals(2, compiled.triangleMergedRanges().length,
            "Different transforms should produce separate merged ranges");
    }

    @Test
    void testPrimitiveBufferMergesSameTransformTriangles() {
        // Setup
        TessellatorPrimitiveBuffer buffer = new TessellatorPrimitiveBuffer(positionOnly());

        buffer.addDraw(createTrianglesDraw(2, 0));  // 2 triangles
        buffer.addDraw(createTrianglesDraw(3, 0));  // 3 more, same transform

        // Execute
        CompiledPrimitiveBuffers compiled = buffer.finish();

        // Verify: 1 merged range
        assertNotNull(compiled);
        assertEquals(1, compiled.triangleMergedRanges().length,
            "Same transform draws should merge into 1 range");

        // Total vertices: (2+3) triangles * 3 vertices = 15
        assertEquals(15, compiled.triangleMergedRanges()[0].vertexCount(),
            "Merged range should contain all triangle vertices");
    }

    @Test
    void testPrimitiveBufferHandlesMixedLinesAndTriangles() {
        // Setup: Draw with mixed primitives
        TessellatorPrimitiveBuffer buffer = new TessellatorPrimitiveBuffer(positionOnly());
        buffer.addDraw(createMixedPrimitiveDraw(3, 2, 0));  // 3 lines + 2 triangles

        // Execute
        CompiledPrimitiveBuffers compiled = buffer.finish();

        // Verify: Both types present
        assertNotNull(compiled);
        assertTrue(compiled.hasLines(), "Should have lines");
        assertTrue(compiled.hasTriangles(), "Should have triangles");

        // Verify counts
        assertEquals(6, compiled.lineMergedRanges()[0].vertexCount(),
            "Should have 6 line vertices (3 lines * 2)");
        assertEquals(6, compiled.triangleMergedRanges()[0].vertexCount(),
            "Should have 6 triangle vertices (2 triangles * 3)");
    }

    @Test
    void testPrimitiveBufferEmptyReturnsNull() {
        TessellatorPrimitiveBuffer buffer = new TessellatorPrimitiveBuffer(positionOnly());
        CompiledPrimitiveBuffers compiled = buffer.finish();
        assertNull(compiled, "Empty buffer should return null");
    }

    @Test
    void testPrimitiveBufferPerDrawRangesTrackEachDraw() {
        // Setup: Multiple draws
        TessellatorPrimitiveBuffer buffer = new TessellatorPrimitiveBuffer(positionOnly());

        buffer.addDraw(createLinesDraw(2, 0));  // 4 vertices
        buffer.addDraw(createLinesDraw(3, 1));  // 6 vertices (same transform, different cmd index)
        buffer.addDraw(createLinesDraw(1, 2));  // 2 vertices

        // Execute
        CompiledPrimitiveBuffers compiled = buffer.finish();

        // Verify: Per-draw ranges track each individual draw
        assertNotNull(compiled);
        assertEquals(3, compiled.linePerDrawRanges().length,
            "Should have 3 per-draw ranges");

        // Verify individual range sizes
        assertEquals(4, compiled.linePerDrawRanges()[0].vertexCount());
        assertEquals(6, compiled.linePerDrawRanges()[1].vertexCount());
        assertEquals(2, compiled.linePerDrawRanges()[2].vertexCount());
    }

    @Test
    void testDifferentFlagsProduceDifferentFormats() {
        // Test that format-aware compilation uses optimal vertex sizes
        // positionOnly = 12 bytes, positionColor = 16 bytes, fullFormat = 32 bytes

        // Position only (12 bytes per vertex)
        TessellatorPrimitiveBuffer posOnlyBuffer = new TessellatorPrimitiveBuffer(positionOnly());
        posOnlyBuffer.addDraw(createLinesDraw(1, 0));
        CompiledPrimitiveBuffers posOnlyCompiled = posOnlyBuffer.finish();
        assertNotNull(posOnlyCompiled);
        assertEquals(positionOnly(), posOnlyCompiled.flags(), "Should preserve positionOnly flags");

        // Position + Color (16 bytes per vertex)
        TessellatorPrimitiveBuffer posColorBuffer = new TessellatorPrimitiveBuffer(positionColor());
        posColorBuffer.addDraw(createPrimitiveDraw(createMixedPrimitives(1, 0), identity(), positionColor(), 0));
        CompiledPrimitiveBuffers posColorCompiled = posColorBuffer.finish();
        assertNotNull(posColorCompiled);
        assertEquals(positionColor(), posColorCompiled.flags(), "Should preserve positionColor flags");

        // Full format (32 bytes per vertex)
        TessellatorPrimitiveBuffer fullBuffer = new TessellatorPrimitiveBuffer(fullFormat());
        fullBuffer.addDraw(createPrimitiveDraw(createMixedPrimitives(1, 0), identity(), fullFormat(), 0));
        CompiledPrimitiveBuffers fullCompiled = fullBuffer.finish();
        assertNotNull(fullCompiled);
        assertEquals(fullFormat(), fullCompiled.flags(), "Should preserve fullFormat flags");

        // Verify the flags differ (ensuring format selection works)
        assertNotEquals(posOnlyCompiled.flags(), posColorCompiled.flags());
        assertNotEquals(posColorCompiled.flags(), fullCompiled.flags());
    }

    // ==================== Integration Tests ====================

    @Test
    void testBuildOptimizedDisplayListWithLinesOnly() {
        // Setup: Only primitive draws
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        List<AccumulatedPrimitiveDraw> primitiveDraws = new ArrayList<>();
        primitiveDraws.add(createLinesDraw(5, 0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, primitiveDraws, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 VBO for lines
        assertEquals(1, result.ownedVbos().length, "Should have 1 VBO for lines");

        // Verify: 1 DrawRangeCmd
        assertEquals(1, countDrawRangeCmds(optimized), "Should have 1 DrawRangeCmd");
    }

    @Test
    void testBuildOptimizedDisplayListWithTrianglesOnly() {
        // Setup
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        List<AccumulatedPrimitiveDraw> primitiveDraws = new ArrayList<>();
        primitiveDraws.add(createTrianglesDraw(3, 0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, primitiveDraws, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 VBO for triangles
        assertEquals(1, result.ownedVbos().length, "Should have 1 VBO for triangles");
        assertEquals(1, countDrawRangeCmds(optimized), "Should have 1 DrawRangeCmd");
    }

    @Test
    void testBuildOptimizedDisplayListWithMixedPrimitives() {
        // Setup: Lines and triangles
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        List<AccumulatedPrimitiveDraw> primitiveDraws = new ArrayList<>();
        primitiveDraws.add(createLinesDraw(3, 0));
        primitiveDraws.add(createTrianglesDraw(2, 1));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, primitiveDraws, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 2 VBOs (one for lines, one for triangles)
        assertEquals(2, result.ownedVbos().length,
            "Should have 2 VBOs (lines + triangles)");
        assertEquals(2, countDrawRangeCmds(optimized),
            "Should have 2 DrawRangeCmds");
    }

    @Test
    void testBuildOptimizedDisplayListWithQuadsAndPrimitives() {
        // Setup: Mixed quads and primitives
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        quadDraws.add(createSimpleDraw(0));  // 1 quad

        List<AccumulatedPrimitiveDraw> primitiveDraws = new ArrayList<>();
        primitiveDraws.add(createLinesDraw(2, 1));  // 2 lines

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, primitiveDraws, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 2 VBOs (quads + lines)
        assertEquals(2, result.ownedVbos().length,
            "Should have 2 VBOs (quads + lines)");
        assertEquals(2, countDrawRangeCmds(optimized),
            "Should have 2 DrawRangeCmds");

        // Verify unique VBOs in commands
        Set<VertexBuffer> uniqueVBOs = getUniqueVBOs(optimized);
        assertEquals(2, uniqueVBOs.size(), "Commands should reference 2 unique VBOs");
    }

    @Test
    void testBuildOptimizedDisplayListWithQuadsLinesAndTriangles() {
        // Setup: All three types
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        quadDraws.add(createSimpleDraw(0));

        List<AccumulatedPrimitiveDraw> primitiveDraws = new ArrayList<>();
        primitiveDraws.add(createLinesDraw(2, 1));
        primitiveDraws.add(createTrianglesDraw(2, 2));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, primitiveDraws, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 3 VBOs (quads + lines + triangles)
        assertEquals(3, result.ownedVbos().length,
            "Should have 3 VBOs (quads + lines + triangles)");
        assertEquals(3, countDrawRangeCmds(optimized),
            "Should have 3 DrawRangeCmds");
    }

    @Test
    void testBuildOptimizedDisplayListPrimitiveTransformMerging() {
        // Setup: Multiple draws with same transform should merge
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        List<AccumulatedPrimitiveDraw> primitiveDraws = new ArrayList<>();

        // Add 3 line draws at same transform
        primitiveDraws.add(createLinesDraw(2, 0));
        primitiveDraws.add(createLinesDraw(3, 0));
        primitiveDraws.add(createLinesDraw(1, 0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, primitiveDraws, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 VBO, 1 merged DrawRangeCmd
        assertEquals(1, result.ownedVbos().length, "Should have 1 VBO");
        assertEquals(1, countDrawRangeCmds(optimized),
            "Same-transform draws should merge into 1 DrawRangeCmd");
    }

    @Test
    void testBuildOptimizedDisplayListPrimitiveDifferentTransforms() {
        // Setup: Draws with different transforms
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        List<AccumulatedPrimitiveDraw> primitiveDraws = new ArrayList<>();

        List<ModelPrimitiveView> lines1 = new ArrayList<>();
        lines1.add(createSimpleLine());
        primitiveDraws.add(new AccumulatedPrimitiveDraw(lines1, identity(), positionOnly(), 0));

        List<ModelPrimitiveView> lines2 = new ArrayList<>();
        lines2.add(createSimpleLine());
        primitiveDraws.add(new AccumulatedPrimitiveDraw(lines2, translate(1, 0, 0), positionOnly(), 1));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, primitiveDraws, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: 1 VBO (same format), 2 DrawRangeCmds (different transforms)
        assertEquals(1, result.ownedVbos().length,
            "Same-type primitives should share VBO");
        assertEquals(2, countDrawRangeCmds(optimized),
            "Different transforms should produce separate DrawRangeCmds");
    }

    @Test
    void testBuildOptimizedDisplayListNullPrimitiveDraws() {
        // Setup: null primitiveDraws list (backwards compatibility)
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        quadDraws.add(createSimpleDraw(0));

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute: Pass null for primitiveDraws
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, null, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: Works normally with just quads
        assertEquals(1, result.ownedVbos().length, "Should have 1 VBO for quads");
        assertEquals(1, countDrawRangeCmds(optimized), "Should have 1 DrawRangeCmd");
    }

    @Test
    void testBuildOptimizedDisplayListEmptyPrimitiveDraws() {
        // Setup: Empty primitiveDraws list
        List<AccumulatedDraw> quadDraws = new ArrayList<>();
        quadDraws.add(createSimpleDraw(0));

        List<AccumulatedPrimitiveDraw> primitiveDraws = new ArrayList<>();

        List<DisplayListCommand> commands = new ArrayList<>();

        // Execute
        OptimizedListResult result = DisplayListManager.buildOptimizedDisplayList(
            commands, quadDraws, primitiveDraws, 1);
        DisplayListCommand[] optimized = result.commands();

        // Verify: Works normally with just quads
        assertEquals(1, result.ownedVbos().length, "Should have 1 VBO for quads");
        assertEquals(1, countDrawRangeCmds(optimized), "Should have 1 DrawRangeCmd");
    }
}
