package net.minecraft.client.renderer;

/**
 * Compile stub for MC's Tessellator. DirectTessellator (GTNHLib) extends this.
 */
public class Tessellator {

    public int[] rawBuffer;
    public int rawBufferSize;
    public int rawBufferIndex;
    public int vertexCount;
    public int addedVertices;
    public int drawMode;
    public boolean isDrawing;
    public boolean hasColor;
    public boolean hasTexture;
    public boolean hasBrightness;
    public boolean hasNormals;

    public Tessellator() {}

    public int draw() {return 0;}

    public void reset() {}

    public void startDrawing(int mode) {}
}
