package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;

/**
 * Helper record pairing a DrawRange with its VBO and vertex format flags.
 * Used for sorting ranges by command index while retaining VBO reference.
 * Works for both quad ranges (from CompiledFormatBuffer) and line ranges (from CompiledLineBuffer).
 */
@Desugar
record DrawRangeWithBuffer(
    DrawRange range,
    VertexBuffer vbo,
    CapturingTessellator.Flags flags,
    DrawSource source
) {
    boolean hasBrightness() {
        return flags != null && flags.hasBrightness;
    }

    /**
     * Source of a draw command for debug logging.
     */
    enum DrawSource {
        TESSELLATOR_QUADS("tessellator quads"),
        TESSELLATOR_LINES("tessellator lines"),
        TESSELLATOR_TRIANGLES("tessellator triangles"),
        IMMEDIATE_MODE_LINES("immediate mode lines");

        private final String description;

        DrawSource(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
