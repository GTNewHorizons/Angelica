package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import org.joml.Matrix4f;

/**
 * Represents a vertex range within a shared VBO, along with its transform.
 * Used during format-based batching to track where each draw's vertices are stored.
 *
 * <p>The restoreData field holds last vertex attributes for GL state restoration
 * after VBO rendering (immediate mode draws only). Null for tessellator draws.
 */
@Desugar
public record DrawRange(
    int startVertex,
    int vertexCount,
    Matrix4f transform,
    int commandIndex,
    AccumulatedDraw.RestoreData restoreData
) {
    DrawRange(int startVertex, int vertexCount, Matrix4f transform, int commandIndex) {
        this(startVertex, vertexCount, transform, commandIndex, null);
    }
}
