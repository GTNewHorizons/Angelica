package com.gtnewhorizons.angelica.rendering.tesr;

public final class TemplateBuffer {

    public final int[] data;
    final int[] work;
    public final int vertexCount;
    public final int drawMode;

    public TemplateBuffer(int[] data, int vertexCount, int drawMode) {
        this.data = data;
        this.work = data.clone();
        this.vertexCount = vertexCount;
        this.drawMode = drawMode;
    }
}
