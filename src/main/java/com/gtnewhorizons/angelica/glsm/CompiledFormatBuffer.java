package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;

/**
 * A compiled VBO with its associated draw ranges.
 * All ranges reference vertices within this single VBO.
 * <p>
 * Contains two range arrays:
 * <ul>
 *   <li><b>mergedRanges</b>: For optimized path - consecutive same-transform draws merged</li>
 *   <li><b>perDrawRanges</b>: For unoptimized path - 1:1 with original draws</li>
 * </ul>
 */
@Desugar
public record CompiledFormatBuffer(
    VertexBuffer vbo,
    CapturingTessellator.Flags flags,
    DrawRange[] mergedRanges,
    DrawRange[] perDrawRanges
) {}
