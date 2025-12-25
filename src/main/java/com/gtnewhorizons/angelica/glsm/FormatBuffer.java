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
 * Tracks merged ranges (same-transform draws combined) and per-draw ranges (1:1).
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
    private int pendingGeneration = -1;
    private int pendingStateGen = -1;
    // Track last restore data for merged draws (uses the LAST draw's data)
    private AccumulatedDraw.RestoreData pendingRestoreData = null;

    FormatBuffer(CapturingTessellator.Flags flags) {
        this.flags = flags;
    }

    /** Add a draw, tracking both merged and per-draw ranges. */
    void addDraw(AccumulatedDraw draw) {
        final int vertexCount = draw.quads.size() * 4;  // 4 vertices per quad

        // Always track per-draw range for unoptimized path (includes restoreData)
        perDrawRanges.add(new DrawRange(
            currentVertexOffset, vertexCount, draw.transform, draw.commandIndex, draw.restoreData
        ));

        // Merge logic for optimized path:
        // - same matrixGeneration = same matrix transform context
        // - same stateGeneration = no state commands (draw barriers) between draws
        boolean canMerge = pendingGeneration != -1
            && pendingGeneration == draw.matrixGeneration
            && pendingStateGen == draw.stateGeneration;

        if (canMerge) {
            // Extend the pending range
            pendingVertexCount += vertexCount;
            // Update restoreData to the LAST draw's data (used after merged draw completes)
            pendingRestoreData = draw.restoreData;
        } else {
            // Flush pending range if any
            flushPendingRange();

            // Start new pending range
            pendingStartVertex = currentVertexOffset;
            pendingVertexCount = vertexCount;
            pendingTransform = new Matrix4f(draw.transform);
            pendingCommandIndex = draw.commandIndex;
            pendingGeneration = draw.matrixGeneration;
            pendingStateGen = draw.stateGeneration;
            pendingRestoreData = draw.restoreData;
        }

        allQuads.addAll(draw.quads);
        currentVertexOffset += vertexCount;
    }

    private void flushPendingRange() {
        if (pendingGeneration != -1) {
            mergedRanges.add(new DrawRange(
                pendingStartVertex, pendingVertexCount, pendingTransform, pendingCommandIndex, pendingRestoreData
            ));
            pendingTransform = null;
            pendingGeneration = -1;
            pendingStateGen = -1;
            pendingRestoreData = null;
        }
    }

    /** Compile accumulated quads into a VBO. */
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
