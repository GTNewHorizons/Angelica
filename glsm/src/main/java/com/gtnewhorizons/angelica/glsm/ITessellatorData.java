package com.gtnewhorizons.angelica.glsm;

/**
 * Tessellator data needed by {@link com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer}.
 */
public interface ITessellatorData {

    boolean isDrawing();
    void setDrawing(boolean drawing);
    int getVertexCount();
    int[] getRawBuffer();
    int getRawBufferIndex();
    int getRawBufferSize();
    void setRawBufferSize(int size);
    void setRawBuffer(int[] buffer);
    int getDrawMode();
    boolean hasTexture();
    boolean hasColor();
    boolean hasNormals();
    boolean hasBrightness();
    void angelica$reset();
}
