package com.gtnewhorizons.angelica.glsm;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;

/**
 * Helper record pairing a DrawRange with its VBO and brightness flag.
 * Used for sorting ranges by command index while retaining VBO reference.
 * Works for both quad ranges (from CompiledFormatBuffer) and line ranges (from CompiledLineBuffer).
 */
@Desugar
record DrawRangeWithBuffer(DrawRange range, VertexBuffer vbo, boolean hasBrightness) {}
