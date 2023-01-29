package net.minecraft.client.renderer;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import net.minecraft.client.shader.TesselatorVertexState;

public class Tessellator {

    private static int nativeBufferSize;
    private static int trivertsInBuffer;
    public static boolean renderingWorldRenderer;
    public boolean defaultTexture;
    private int rawBufferSize;
    public int textureID;

    /** The byte buffer used for GL allocation. */
    public ByteBuffer byteBuffer;
    /** The same memory as byteBuffer, but referenced as an integer buffer. */
    public IntBuffer intBuffer;
    /** The same memory as byteBuffer, but referenced as an float buffer. */
    public FloatBuffer floatBuffer;
    /** The same memory as byteBuffer, but referenced as an short buffer. */
    public ShortBuffer shortBuffer;
    /** Raw integer array. */
    public int[] rawBuffer;
    /** The number of vertices to be drawn in the next draw call. Reset to 0 between draw calls. */
    public int vertexCount;
    /** The first coordinate to be used for the texture. */
    public double textureU;
    /** The second coordinate to be used for the texture. */
    public double textureV;

    public int brightness;
    /** The color (RGBA) value to be used for the following draw call. */
    public int color;
    /** Whether the current draw object for this tessellator has color values. */
    public boolean hasColor;
    /** Whether the current draw object for this tessellator has texture coordinates. */
    public boolean hasTexture;

    public boolean hasBrightness;
    /** Whether the current draw object for this tessellator has normal values. */
    public boolean hasNormals;
    /** The index into the raw buffer to be used for the next data. */
    public int rawBufferIndex;
    /**
     * The number of vertices manually added to the given draw call. This differs from vertexCount because it adds extra
     * vertices when converting quads to triangles.
     */
    public int addedVertices;
    /** Disables all color information for the following draw call. */
    public boolean isColorDisabled;
    /** The draw mode currently being used by the tessellator. */
    public int drawMode;
    /** An offset to be applied along the x-axis for all vertices in this draw call. */
    public double xOffset;
    /** An offset to be applied along the y-axis for all vertices in this draw call. */
    public double yOffset;
    /** An offset to be applied along the z-axis for all vertices in this draw call. */
    public double zOffset;
    /** The normal to be applied to the face being drawn. */
    public int normal;
    /** The static instance of the Tessellator. */
    public static final Tessellator instance = new Tessellator();
    /** Whether this tessellator is currently in draw mode. */
    public boolean isDrawing;
    /** The size of the buffers used (in integers). */
    public int bufferSize;

    public float midTextureU;
    public float midTextureV;
    public float normalX;
    public float normalY;
    public float normalZ;
    public float[] vertexPos = new float[16];

    private Tessellator(int p_i1250_1_) {}

    public Tessellator() {}

    /**
     * Draws the data set up in this tessellator and resets the state to prepare for new drawing.
     */
    public int draw() {
        return 0;
    }

    public TesselatorVertexState getVertexState(float p_147564_1_, float p_147564_2_, float p_147564_3_) {
        return null;
    }

    public void setVertexState(TesselatorVertexState p_147565_1_) {}

    /**
     * Clears the tessellator state in preparation for new drawing.
     */
    public void reset() {}

    /**
     * Sets draw mode in the tessellator to draw quads.
     */
    public void startDrawingQuads() {}

    /**
     * Resets tessellator state and prepares for drawing (with the specified draw mode).
     */
    public void startDrawing(int p_78371_1_) {}

    /**
     * Sets the texture coordinates.
     */
    public void setTextureUV(double p_78385_1_, double p_78385_3_) {}

    public void setBrightness(int p_78380_1_) {}

    /**
     * Sets the RGB values as specified, converting from floats between 0 and 1 to integers from 0-255.
     */
    public void setColorOpaque_F(float p_78386_1_, float p_78386_2_, float p_78386_3_) {}

    /**
     * Sets the RGBA values for the color, converting from floats between 0 and 1 to integers from 0-255.
     */
    public void setColorRGBA_F(float p_78369_1_, float p_78369_2_, float p_78369_3_, float p_78369_4_) {}

    /**
     * Sets the RGB values as specified, and sets alpha to opaque.
     */
    public void setColorOpaque(int p_78376_1_, int p_78376_2_, int p_78376_3_) {}

    /**
     * Sets the RGBA values for the color. Also clamps them to 0-255.
     */
    public void setColorRGBA(int p_78370_1_, int p_78370_2_, int p_78370_3_, int p_78370_4_) {}

    public void func_154352_a(byte p_154352_1_, byte p_154352_2_, byte p_154352_3_) {}

    /**
     * Adds a vertex specifying both x,y,z and the texture u,v for it.
     */
    public void addVertexWithUV(double p_78374_1_, double p_78374_3_, double p_78374_5_, double p_78374_7_,
            double p_78374_9_) {}

    /**
     * Adds a vertex with the specified x,y,z to the current draw call. It will trigger a draw() if the buffer gets
     * full.
     */
    public void addVertex(double p_78377_1_, double p_78377_3_, double p_78377_5_) {}

    /**
     * Sets the color to the given opaque value (stored as byte values packed in an integer).
     */
    public void setColorOpaque_I(int p_78378_1_) {}

    /**
     * Sets the color to the given color (packed as bytes in integer) and alpha values.
     */
    public void setColorRGBA_I(int p_78384_1_, int p_78384_2_) {}

    /**
     * Disables colors for the current draw call.
     */
    public void disableColor() {}

    /**
     * Sets the normal for the current draw call.
     */
    public void setNormal(float p_78375_1_, float p_78375_2_, float p_78375_3_) {}

    /**
     * Sets the translation for all vertices in the current draw call.
     */
    public void setTranslation(double p_78373_1_, double p_78373_3_, double p_78373_5_) {}

    /**
     * Offsets the translation for all vertices in the current draw call.
     */
    public void addTranslation(float p_78372_1_, float p_78372_2_, float p_78372_3_) {}
}
