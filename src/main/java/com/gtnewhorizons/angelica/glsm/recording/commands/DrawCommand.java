package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;

/**
 * Represents a single draw call within a compiled display list.
 * Each DrawCommand corresponds to one glBegin/glEnd block.
 */
@Desugar
public record DrawCommand(
    int firstVertex,
    int vertexCount,
    int primitiveType
) {}
