package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;

/**
 * Result of building an optimized display list.
 * Contains both the commands and the shared VBOs that need lifecycle management.
 */
@Desugar
record OptimizedListResult(DisplayListCommand[] commands, VertexBuffer[] ownedVbos) {}
