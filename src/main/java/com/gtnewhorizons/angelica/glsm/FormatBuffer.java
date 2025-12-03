package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates geometry for a single vertex format during display list compilation.
 * All draws with the same format are collected here, then compiled into a single VBO.
 * <p>
 * Tracks two types of ranges:
 * <ul>
 *   <li><b>mergedRanges</b>: Consecutive same-transform draws merged for optimized path</li>
 *   <li><b>perDrawRanges</b>: One range per input draw for unoptimized path (1:1 with addDraw calls)</li>
 * </ul>
 */
class FormatBuffer {
    final CapturingTessellator.Flags flags;
    final List<ModelQuadViewMutable> allQuads = new ArrayList<>();

    // Merged ranges for optimized path (consecutive same-transform draws combined)
    final List<DrawRange> mergedRanges = new ArrayList<>();

    // Per-draw ranges for unoptimized path (1:1 with input draws)
    final List<DrawRange> perDrawRanges = new ArrayList<>();

    int currentVertexOffset = 0;

    // Track the current range being built for merging consecutive same-transform draws
    private int pendingStartVertex = -1;
    private int pendingVertexCount = 0;
    private Matrix4f pendingTransform = null;
    private int pendingCommandIndex = -1;

    FormatBuffer(CapturingTessellator.Flags flags) {
        this.flags = flags;
    }

    /**
     * Add a draw to this format buffer.
     * Tracks both merged (for optimized) and per-draw (for unoptimized) ranges.
     */
    void addDraw(AccumulatedDraw draw) {
        int vertexCount = draw.quads.size() * 4;  // 4 vertices per quad

        // Always track per-draw range for unoptimized path
        perDrawRanges.add(new DrawRange(currentVertexOffset, vertexCount, draw.transform, draw.commandIndex));

        // Merge logic for optimized path
        if (pendingTransform != null && pendingTransform.equals(draw.transform)) {
            // Extend the pending range
            pendingVertexCount += vertexCount;
        } else {
            // Flush pending range if any
            flushPendingRange();

            // Start new pending range
            pendingStartVertex = currentVertexOffset;
            pendingVertexCount = vertexCount;
            pendingTransform = new Matrix4f(draw.transform);
            pendingCommandIndex = draw.commandIndex;
        }

        allQuads.addAll(draw.quads);
        currentVertexOffset += vertexCount;
    }

    private void flushPendingRange() {
        if (pendingTransform != null) {
            mergedRanges.add(new DrawRange(pendingStartVertex, pendingVertexCount, pendingTransform, pendingCommandIndex));
            pendingTransform = null;
        }
    }

    /**
     * Compile all accumulated quads into a single VBO.
     * @return The compiled buffer with VBO and both range types
     */
    CompiledFormatBuffer finish() {
        // Flush any remaining pending range
        flushPendingRange();

        VertexBuffer vbo = DisplayListManager.compileQuads(allQuads, flags);
        return new CompiledFormatBuffer(
            vbo,
            flags,
            mergedRanges.toArray(new DrawRange[0]),
            perDrawRanges.toArray(new DrawRange[0])
        );
    }
}
