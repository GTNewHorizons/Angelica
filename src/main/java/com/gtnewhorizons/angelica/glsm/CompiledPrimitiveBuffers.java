package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;

/**
 * Compiled VBOs containing tessellator primitives (lines and triangles) for a single vertex format.
 * Separate VBOs are used for lines (GL_LINES) and triangles (GL_TRIANGLES) since they have different draw modes.
 * <p>
 * Contains range arrays for both optimized (merged) and unoptimized (per-draw) paths.
 * Similar to CompiledFormatBuffer but handles primitives instead of quads.
 */
@Desugar
record CompiledPrimitiveBuffers(
    CapturingTessellator.Flags flags,
    VertexBuffer lineVbo,
    DrawRange[] lineMergedRanges,
    DrawRange[] linePerDrawRanges,
    VertexBuffer triangleVbo,
    DrawRange[] triangleMergedRanges,
    DrawRange[] trianglePerDrawRanges
) {
    public boolean hasLines() {
        return lineVbo != null;
    }

    public boolean hasTriangles() {
        return triangleVbo != null;
    }
}
