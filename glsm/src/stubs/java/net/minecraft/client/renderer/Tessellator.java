package net.minecraft.client.renderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Compile stub for MC's Tessellator. DirectTessellator (GTNHLib) extends this.
 */
public class Tessellator {

    public static ByteBuffer byteBuffer = ByteBuffer.allocateDirect(0x200000 * 4).order(ByteOrder.nativeOrder());
    public static boolean renderingWorldRenderer;
    public static final Tessellator instance = new Tessellator();

    public boolean defaultTexture;
    public int[] rawBuffer;
    public int rawBufferSize;
    public int textureID;
    public int rawBufferIndex;
    public int vertexCount;
    public int addedVertices;
    public int drawMode;
    public boolean isDrawing;
    public boolean isColorDisabled;
    public boolean hasColor;
    public boolean hasTexture;
    public boolean hasBrightness;
    public boolean hasNormals;
    public double textureU;
    public double textureV;
    public int brightness;
    public int color;
    public int normal;
    public double xOffset;
    public double yOffset;
    public double zOffset;
    public int bufferSize;

    public Tessellator() {}

    public int draw() { return 0; }

    public void reset() {}

    public void startDrawing(int mode) {}
}
