package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import org.joml.Matrix4f;

/**
 * Represents a vertex range within a shared VBO, along with its transform.
 * Used during format-based batching to track where each draw's vertices are stored.
 */
@Desugar
record DrawRange(int startVertex, int vertexCount, Matrix4f transform, int commandIndex) {}
