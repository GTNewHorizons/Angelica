package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.VertexAttribState;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Arrays;

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
     * Set the current lightmap coordinate as packed brightness.
     * Called by GLStateManager.glMultiTexCoord2f for unit 1 during recording/drawing.
     */
    public static void setLightmapCoord(float s, float t) {
        tessellator().setBrightness(((int) t << 16) | ((int) s & 0xFFFF));
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

    private static final int LOC_POSITION = Usage.POSITION.getAttributeLocation();
    private static final int LOC_COLOR = Usage.COLOR.getAttributeLocation();
    private static final int LOC_UV0 = Usage.PRIMARY_UV.getAttributeLocation();
    private static final int LOC_UV1 = Usage.SECONDARY_UV.getAttributeLocation();
    private static final int LOC_NORMAL = Usage.NORMAL.getAttributeLocation();
    private static final int NUM_ATTRIBS = 1 + Arrays.stream(Usage.values())
        .mapToInt(Usage::getAttributeLocation)
            .max().orElse(0);

    private static final class AttribReader {
        ByteBuffer data;
        int stride;
        int baseOffset;
        int size;
        int loc;
        int type;
        boolean normalized;
    }

    private static final AttribReader[] readers = new AttribReader[NUM_ATTRIBS];
    private static int nonPositionCount;
    private static final int[] nonPositionIndices = new int[NUM_ATTRIBS - 1];
    private static int positionReaderIndex = -1;

    static {
        for (int i = 0; i < NUM_ATTRIBS; i++) {
            readers[i] = new AttribReader();
        }
    }

    /**
     * Process a glDrawArrays call during display list recording.
     * Reads vertex data from VBOs or client memory referenced by VertexAttribState and converts to immediate mode geometry.
     */
    public static DirectTessellator processDrawArraysFromAttribs(int mode, int first, int count) {
        final AttribSnapshot snap = AttribSnapshot.snapshot(first, count);
        try {
            prepareReaders(snap);
            if (positionReaderIndex < 0) return null;
            return readVerticesFromBuffer(mode, first, count);
        } finally {
            snap.free();
        }
    }

    private static void prepareReaders(AttribSnapshot snap) {
        nonPositionCount = 0;
        positionReaderIndex = -1;

        for (int i = 0; i < NUM_ATTRIBS; i++) {
            final AttribSnapshot.AttribDesc d = snap.get(i);
            if (d == null) continue;

            final AttribReader r = readers[i];
            r.data = d.readBuffer();
            r.stride = d.effectiveStride();
            r.baseOffset = (int) (d.offset() - d.readBufferBaseOffset());
            r.size = d.size();
            r.loc = i;
            r.type = d.type();
            r.normalized = d.normalized();

            if (i == LOC_POSITION) {
                positionReaderIndex = i;
            } else {
                nonPositionIndices[nonPositionCount++] = i;
            }
        }
    }

    private static DirectTessellator readVerticesFromBuffer(int mode, int first, int count) {
        begin(mode);
        final DirectTessellator t = tessellator();

        for (int i = first; i < first + count; i++) {
            readVertexFromBuffer(t, i);
        }

        return end();
    }

    public static int readIndex(ByteBuffer ebo, int indexType, int eboOffset, int i) {
        return switch (indexType) {
            case GL11.GL_UNSIGNED_BYTE -> ebo.get(eboOffset + i) & 0xFF;
            case GL11.GL_UNSIGNED_SHORT -> ebo.getShort(eboOffset + i * 2) & 0xFFFF;
            case GL11.GL_UNSIGNED_INT -> ebo.getInt(eboOffset + i * 4);
            default -> i;
        };
    }

    private static void readVertexFromBuffer(DirectTessellator t, int vertexIndex) {
        for (int i = 0; i < nonPositionCount; i++) {
            final AttribReader r = readers[nonPositionIndices[i]];
            final int base = r.baseOffset + vertexIndex * r.stride;
            emitAttrib(t, r, base);
        }

        if (positionReaderIndex >= 0) {
            final AttribReader r = readers[positionReaderIndex];
            final int base = r.baseOffset + vertexIndex * r.stride;
            t.addVertex(
                readComponent(r, base, 0),
                readComponent(r, base, 1),
                (r.size >= 3) ? readComponent(r, base, 2) : 0.0f);
        }
    }

    private static void emitAttrib(DirectTessellator t, AttribReader r, int base) {
        if (r.loc == LOC_NORMAL) {
            t.setNormal(
                readComponent(r, base, 0),
                readComponent(r, base, 1),
                readComponent(r, base, 2));
        } else if (r.loc == LOC_UV0) {
            t.setTextureUV(
                readComponent(r, base, 0),
                (r.size >= 2) ? readComponent(r, base, 1) : 0.0f);
        } else if (r.loc == LOC_UV1) {
            if (r.type == GL11.GL_SHORT) {
                final int s = r.data.getShort(base) & 0xFFFF;
                final int u = (r.size >= 2) ? (r.data.getShort(base + 2) & 0xFFFF) : 0;
                t.setBrightness((u << 16) | s);
            } else if (r.type == GL11.GL_FLOAT) {
                final int s = (int) r.data.getFloat(base);
                final int u = (r.size >= 2) ? (int) r.data.getFloat(base + 4) : 0;
                t.setBrightness((u << 16) | (s & 0xFFFF));
            }
        } else if (r.loc == LOC_COLOR) {
            final float cr = readComponent(r, base, 0);
            final float cg = readComponent(r, base, 1);
            final float cb = readComponent(r, base, 2);
            final float ca = (r.size >= 4) ? readComponent(r, base, 3) : 1.0f;
            t.setColorRGBA((int) (cr * 255.0f), (int) (cg * 255.0f), (int) (cb * 255.0f), (int) (ca * 255.0f));
        }
    }

    private static float readComponent(AttribReader r, int base, int component) {
        return VertexAttribState.Attrib.readComponent(r.type, r.normalized, r.data, base, component);
    }

}
