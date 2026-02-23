package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.ClientArrayState;
import org.lwjgl.opengl.GL11;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Records immediate mode GL calls (glBegin/glEnd/glVertex) via {@link DirectTessellator}.
 * Used both during display list compilation and for live immediate mode emulation in core profile.
 */
public final class ImmediateModeRecorder {
    private static final DirectTessellator tessellator = new DirectTessellator(TessellatorManager.DEFAULT_BUFFER_SIZE);

    /** Separate tessellator for the splash thread. Nulled after splash completes. */
    private static DirectTessellator splashTessellator;

    private ImmediateModeRecorder() {

    }

    public static void initSplashTessellator() {
        splashTessellator = new DirectTessellator(TessellatorManager.DEFAULT_BUFFER_SIZE);
    }

    public static void destroySplashTessellator() {
        splashTessellator = null;
    }

    private static DirectTessellator tessellator() {
        final DirectTessellator splash = splashTessellator;
        if (splash != null && Thread.currentThread() != GLStateManager.getMainThread()) {
            return splash;
        }
        return tessellator;
    }

    public static DirectTessellator getInternalTessellator() {
        return tessellator();
    }

    public static boolean isDrawing() {
        return tessellator().isDrawing;
    }

    /**
     * Set the current texture coordinate.
     * Called by GLStateManager.glTexCoord*.
     */
    public static void setTexCoord(float s, float t) {
        tessellator().setTextureUV(s, t);
    }

    /**
     * Set the current normal vector.
     * Called by GLStateManager.glNormal*.
     */
    public static void setNormal(float x, float y, float z) {
        tessellator().setNormal(x, y, z);
    }

    /**
     * Set the current color on the internal tessellator.
     * Called by GLStateManager.glColor* when inside a glBegin/glEnd block.
     */
    public static void setColor(float r, float g, float b, float a) {
        tessellator().setColorRGBA((int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f), (int) (a * 255.0f));
    }

    /**
     * Start recording a new primitive.
     * Resets the tessellator to clear any data from the previous draw.
     *
     * @param primitiveType GL primitive type (GL_QUADS, GL_TRIANGLES, etc.)
     */
    public static void begin(int primitiveType) {
        final DirectTessellator t = tessellator();
        t.reset();
        t.startDrawing(primitiveType);
    }

    /**
     * Start recording a new primitive with initial color sync from GLSM cache.
     * Used for live immediate mode emulation only (not display list recording).
     */
    public static void beginLive(int primitiveType) {
        final DirectTessellator t = tessellator();
        t.reset();
        t.startDrawing(primitiveType);
        final var c = GLStateManager.getColor();
        t.setColorRGBA((int) (c.getRed() * 255.0f), (int) (c.getGreen() * 255.0f), (int) (c.getBlue() * 255.0f), (int) (c.getAlpha() * 255.0f));
    }

    /**
     * End the current primitive, convert to quads/lines, and return them immediately.
     * This allows the caller to create AccumulatedDraw at the correct command position.
     *
     * @return Result containing quads, lines, and flags, or null if no geometry was produced
     */
    public static DirectTessellator end() {
        final DirectTessellator t = tessellator();
        if (!t.isDrawing) {
            throw new IllegalStateException("glEnd called without glBegin");
        }

        t.isDrawing = false;

        if (t.isEmpty()) return null;

        return t;
    }

    /**
     * Record a vertex with current attributes.
     * Color is read from GLStateManager at call time.
     */
    public static void vertex(float x, float y, float z) {
        final DirectTessellator t = tessellator();
        if (!t.isDrawing) {
            throw new IllegalStateException("glVertex called outside glBegin/glEnd");
        }
        t.addVertex(x, y, z);
    }

    /**
     * Check if any immediate mode geometry has been captured.
     *
     * <p>Note: With immediate flush mode, this will typically return false as geometry
     * is returned from end() immediately. This method is kept for edge cases
     * where primitives might fail to convert (e.g., incomplete quads).
     */
    public static boolean hasGeometry() {
        return tessellator().vertexCount != 0;
    }

    /**
     * Reset all state including current texcoord/normal.
     * Called when starting a new display list.
     */
    public static void reset() {
        tessellator().reset();
    }

    /**
     * Process a glDrawArrays call during display list recording.
     * Reads vertex data from client arrays (tracked globally in GLStateManager) and converts to immediate mode geometry.
     *
     * @param mode Primitive type (GL_QUADS, GL_LINES, etc.)
     * @param first Starting index in arrays
     * @param count Number of vertices
     * @return Result containing converted geometry, or null if no geometry produced
     */
    public static DirectTessellator processDrawArrays(int mode, int first, int count) {
        final ClientArrayState cas = GLStateManager.getClientArrayState();
        final Buffer vertexPointer = cas.isVertexArrayEnabled() ? cas.getVertexPointer() : null;
        final Buffer colorPointer = cas.isColorArrayEnabled() ? cas.getColorPointer() : null;
        final Buffer normalPointer = cas.isNormalArrayEnabled() ? cas.getNormalPointer() : null;
        final Buffer texCoordPointer = cas.isTexCoordArrayEnabled() ? cas.getTexCoordPointer() : null;

        return convertClientArrays(
            mode, first, count,
            vertexPointer,
            cas.getVertexPointerType(), cas.getVertexPointerSize(), cas.getVertexPointerStride(),
            colorPointer,
            cas.getColorPointerType(), cas.getColorPointerSize(), cas.getColorPointerStride(),
            normalPointer,
            cas.getNormalPointerType(), cas.getNormalPointerStride(),
            texCoordPointer,
            cas.getTexCoordPointerType(), cas.getTexCoordPointerSize(), cas.getTexCoordPointerStride()
        );
    }

    /**
     * Convert client array data to immediate mode.
     * Called by glDrawArrays during display list recording.
     * Like Mesa's save_DrawArrays, reads vertex data from arrays and emits as immediate mode.
     */
    public static DirectTessellator convertClientArrays(
        int mode, int first, int count,
        java.nio.Buffer vertexPointer, int vertexType, int vertexSize, int vertexStride,
        java.nio.Buffer colorPointer, int colorType, int colorSize, int colorStride,
        java.nio.Buffer normalPointer, int normalType, int normalStride,
        java.nio.Buffer texCoordPointer, int texCoordType, int texCoordSize, int texCoordStride
    ) {
        begin(mode);
        final DirectTessellator t = tessellator();

        // Hoist type checks and stride calculations outside the loop for performance.
        // Mesa's algorithm: offset_bytes = index * effective_stride
        // effective_stride = (stride == 0) ? (numComponents * sizeof(element)) : stride

        // Determine color reader
        final ArrayReader colorReader;
        if (colorPointer == null) {
            colorReader = null;
        } else if (colorType == GL11.GL_FLOAT && colorPointer instanceof FloatBuffer fb) {
            final int colorEffectiveStride = (colorStride == 0) ? colorSize * 4 : colorStride;
            final int strideInFloats = colorEffectiveStride / 4;
            colorReader = (idx) -> {
                final int offset = idx * strideInFloats;
                final float r = fb.get(offset);
                final float g = fb.get(offset + 1);
                final float b = fb.get(offset + 2);
                final float a = (colorSize == 4) ? fb.get(offset + 3) : 1.0f;
                GLStateManager.glColor4f(r, g, b, a);
            };
        } else if ((colorType == GL11.GL_UNSIGNED_BYTE || colorType == GL11.GL_BYTE) && colorPointer instanceof ByteBuffer bb) {
            final int colorEffectiveStride = (colorStride == 0) ? colorSize : colorStride;
            colorReader = (idx) -> {
                final int offset = idx * colorEffectiveStride;
                final float r = (bb.get(offset) & 0xFF) / 255.0f;
                final float g = (bb.get(offset + 1) & 0xFF) / 255.0f;
                final float b = (bb.get(offset + 2) & 0xFF) / 255.0f;
                final float a = (colorSize == 4) ? (bb.get(offset + 3) & 0xFF) / 255.0f : 1.0f;
                GLStateManager.glColor4f(r, g, b, a);
            };
        } else {
            // Unsupported color type - use white
            colorReader = (idx) -> GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Determine normal reader
        final ArrayReader normalReader;
        if (normalPointer == null) {
            normalReader = null;
        } else if (normalType == GL11.GL_FLOAT && normalPointer instanceof FloatBuffer fb) {
            final int effectiveStride = (normalStride == 0) ? 3 * 4 : normalStride;
            final int strideInFloats = effectiveStride / 4;
            normalReader = (idx) -> {
                final int offset = idx * strideInFloats;
                setNormal(fb.get(offset), fb.get(offset + 1), fb.get(offset + 2));
            };
        } else if (normalType == GL11.GL_BYTE && normalPointer instanceof ByteBuffer bb) {
            final int effectiveStride = (normalStride == 0) ? 3 : normalStride;
            normalReader = (idx) -> {
                final int offset = idx * effectiveStride;
                setNormal(bb.get(offset) / 127.0f, bb.get(offset + 1) / 127.0f, bb.get(offset + 2) / 127.0f);
            };
        } else {
            normalReader = null;
        }

        // Determine texcoord reader
        final ArrayReader texCoordReader;
        if (texCoordPointer == null) {
            texCoordReader = null;
        } else if (texCoordType == GL11.GL_FLOAT && texCoordPointer instanceof FloatBuffer fb) {
            final int effectiveStride = (texCoordStride == 0) ? texCoordSize * 4 : texCoordStride;
            final int strideInFloats = effectiveStride / 4;
            texCoordReader = (idx) -> {
                final int offset = idx * strideInFloats;
                final float s = fb.get(offset);
                final float tt = (texCoordSize >= 2) ? fb.get(offset + 1) : 0.0f;
                setTexCoord(s, tt);
            };
        } else {
            texCoordReader = null;
        }

        // Determine vertex reader
        final ArrayReader vertexReader;
        if (vertexPointer == null) {
            vertexReader = null;
        } else if (vertexType == GL11.GL_FLOAT && vertexPointer instanceof FloatBuffer fb) {
            final int effectiveStride = (vertexStride == 0) ? vertexSize * 4 : vertexStride;
            final int strideInFloats = effectiveStride / 4;
            vertexReader = (idx) -> {
                final int offset = idx * strideInFloats;
                final float x = fb.get(offset);
                final float y = fb.get(offset + 1);
                final float z = (vertexSize >= 3) ? fb.get(offset + 2) : 0.0f;
                t.addVertex(x, y, z);
            };
        } else if (vertexType == GL11.GL_DOUBLE && vertexPointer instanceof DoubleBuffer db) {
            final int effectiveStride = (vertexStride == 0) ? vertexSize * 8 : vertexStride;
            final int strideInDoubles = effectiveStride / 8;
            vertexReader = (idx) -> {
                final int offset = idx * strideInDoubles;
                final float x = (float) db.get(offset);
                final float y = (float) db.get(offset + 1);
                final float z = (vertexSize >= 3) ? (float) db.get(offset + 2) : 0.0f;
                t.addVertex(x, y, z);
            };
        } else if (vertexType == GL11.GL_INT && vertexPointer instanceof IntBuffer ib) {
            final int effectiveStride = (vertexStride == 0) ? vertexSize * 4 : vertexStride;
            final int strideInInts = effectiveStride / 4;
            vertexReader = (idx) -> {
                final int offset = idx * strideInInts;
                final float x = ib.get(offset);
                final float y = ib.get(offset + 1);
                final float z = (vertexSize >= 3) ? ib.get(offset + 2) : 0.0f;
                t.addVertex(x, y, z);
            };
        } else if (vertexType == GL11.GL_SHORT && vertexPointer instanceof ShortBuffer sb) {
            final int effectiveStride = (vertexStride == 0) ? vertexSize * 2 : vertexStride;
            final int strideInShorts = effectiveStride / 2;
            vertexReader = (idx) -> {
                final int offset = idx * strideInShorts;
                final float x = sb.get(offset);
                final float y = sb.get(offset + 1);
                final float z = (vertexSize >= 3) ? sb.get(offset + 2) : 0.0f;
                t.addVertex(x, y, z);
            };
        } else {
            // Unsupported vertex type
            vertexReader = null;
        }

        // Main loop - no type checks, just function calls
        for (int i = first; i < first + count; i++) {
            if (normalReader != null) {
                normalReader.read(i);
            }
            if (texCoordReader != null) {
                texCoordReader.read(i);
            }
            if (colorReader != null) {
                colorReader.read(i);
            }
            if (vertexReader != null) {
                vertexReader.read(i);
            }
        }

        return end();
    }

    @FunctionalInterface
    private interface ArrayReader {
        void read(int index);
    }
}
