package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;

/**
 * Compiled VBOs containing tessellator primitives (lines and triangles) with associated draw ranges.
 * Separate VBOs are used for lines (GL_LINES) and triangles (GL_TRIANGLES) since they have different draw modes.
 * <p>
 * Contains range arrays for both optimized (merged) and unoptimized (per-draw) paths.
 */
@Desugar
record CompiledPrimitiveBuffers(
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
