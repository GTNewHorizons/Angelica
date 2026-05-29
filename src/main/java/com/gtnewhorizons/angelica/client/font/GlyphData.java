package com.gtnewhorizons.angelica.client.font;

public final class GlyphData {
    public final float uStart;
    public final float vStart;
    public final float xAdvance;
    public final float glyphW;
    public final float uSize;
    public final float vSize;

    public GlyphData(float uStart, float vStart, float xAdvance, float glyphW, float uSize, float vSize) {
        this.uStart = uStart;
        this.vStart = vStart;
        this.xAdvance = xAdvance;
        this.glyphW = glyphW;
        this.uSize = uSize;
        this.vSize = vSize;
    }
}
