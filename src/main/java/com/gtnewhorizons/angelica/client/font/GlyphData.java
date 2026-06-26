package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;

public final class GlyphData {
    public final float uStart;
    public final float vStart;
    public final float xStart;
    public final float xWidth; // The width of the actual char
    public final float xAdvance; // The amount to advance
    public final float uSize;
    public final float vSize;

    // For MC Fonts (where xStart = 0, glyphW = charWidth - 1, xAdvance = charWidth)
    public GlyphData(float uStart, float vStart, float glyphW, float uSize, float vSize) {
        this(uStart, vStart, 0, glyphW, glyphW + 1, uSize, vSize);
    }

    public GlyphData(float uStart, float vStart, float xStart, float xWidth, float xAdvance, float uSize, float vSize) {
        this.uStart = uStart;
        this.vStart = vStart;
        this.xAdvance = xAdvance + FontConfig.glyphSpacing;
        this.xStart = xStart;
        this.xWidth = xWidth;
        this.uSize = uSize;
        this.vSize = vSize;
    }
}
