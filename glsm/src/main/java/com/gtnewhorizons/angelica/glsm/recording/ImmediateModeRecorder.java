package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.VertexAttribState;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

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

    private static final ByteBuffer[] scratchAttribBuffers = new ByteBuffer[NUM_ATTRIBS];
    private static final long[] scratchAttribBaseOffsets = new long[NUM_ATTRIBS];
    private static final int[] scratchVBOIds = new int[NUM_ATTRIBS];
    private static final ByteBuffer[] scratchVBOBuffers = new ByteBuffer[NUM_ATTRIBS];
    private static final long[] scratchVBOReadOffsets = new long[NUM_ATTRIBS];

    private static final class AttribReader {
        ByteBuffer data;
        int stride;
        int baseOffset;
        int size;
        int loc;
        VertexAttribState.Attrib attrib;
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
        final int readVBOCount = readbackAttribVBOs(first, count);

        try {
            prepareReaders();
            if (positionReaderIndex < 0) return null;
            return readVerticesFromBuffer(mode, first, count);
        } finally {
            freeReadbackBuffers(readVBOCount);
        }
    }

    /**
     * Process a glDrawElements call during display list recording when a VBO is bound.
     */
    public static DirectTessellator processDrawElementsFromVBO(int mode, int indicesCount, int indexType, long indicesOffset, int eboId) {
        if (eboId == 0) return null;

        final int readVBOCount = readbackAttribVBOs(-1, 0);

        try {
            prepareReaders();
            if (positionReaderIndex < 0) return null;

            final int prevEBO = GLStateManager.getBoundEBO();
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
            final int eboSize = GL15.glGetBufferParameteri(GL15.GL_ELEMENT_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
            if (eboSize <= 0) {
                GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEBO);
                return null;
            }

            final int indexSize = VertexAttribState.Attrib.glTypeSizeBytes(indexType);
            final int eboReadOffset = (int) indicesOffset;
            final int eboReadSize = indicesCount * indexSize;
            if (eboReadOffset + eboReadSize > eboSize) {
                GLStateManager.LOGGER.warn("[EBO] Read range {}+{} exceeds buffer size {}", eboReadOffset, eboReadSize, eboSize);
                GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEBO);
                return null;
            }
            final ByteBuffer eboData = MemoryUtilities.memAlloc(eboReadSize);
            GL15.glGetBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, eboReadOffset, eboData);
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEBO);

            try {
                return readVerticesFromBufferIndexed(mode, eboData, indexType, 0, indicesCount);
            } finally {
                MemoryUtilities.memFree(eboData);
            }
        } finally {
            freeReadbackBuffers(readVBOCount);
        }
    }

    /**
     * Read data for all enabled attribs into scratchAttribBuffers.
     * For VBO-backed attribs, reads VBO data. For client-memory attribs, uses the client pointer directly.
     */
    private static int readbackAttribVBOs(int firstVertex, int vertexCount) {
        int readVBOCount = 0;
        final int prevVBO = GLStateManager.getBoundVBO();
        final boolean partialRead = firstVertex >= 0;

        for (int i = 0; i < NUM_ATTRIBS; i++) {
            scratchAttribBuffers[i] = null;
            scratchAttribBaseOffsets[i] = 0;
            final VertexAttribState.Attrib a = VertexAttribState.get(i);
            if (!a.enabled) continue;

            if (a.vboId != 0) {
                // VBO-backed attrib — read from GPU
                ByteBuffer existing = null;
                int existingIdx = -1;
                for (int j = 0; j < readVBOCount; j++) {
                    if (scratchVBOIds[j] == a.vboId) {
                        existing = scratchVBOBuffers[j];
                        existingIdx = j;
                        break;
                    }
                }

                if (existing != null) {
                    scratchAttribBuffers[i] = existing;
                    scratchAttribBaseOffsets[i] = scratchVBOReadOffsets[existingIdx];
                } else {
                    GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, a.vboId);
                    final int bufferSize = GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
                    if (bufferSize <= 0) {
                        GLStateManager.LOGGER.warn("[VBO Readback] GL_BUFFER_SIZE={} for VBO {}", bufferSize, a.vboId);
                        continue;
                    }

                    final long readOffset;
                    final int readSize;
                    if (partialRead) {
                        final int stride = a.effectiveStride();
                        long rangeStart = a.offset + (long) firstVertex * stride;
                        long rangeEnd = a.offset + (long) (firstVertex + vertexCount - 1) * stride + (long) a.size * a.typeSizeBytes();

                        for (int k = 0; k < NUM_ATTRIBS; k++) {
                            if (k == i) continue;
                            final VertexAttribState.Attrib ak = VertexAttribState.get(k);
                            if (!ak.enabled || ak.vboId != a.vboId) continue;
                            final int ks = ak.effectiveStride();
                            rangeStart = Math.min(rangeStart, ak.offset + (long) firstVertex * ks);
                            rangeEnd = Math.max(rangeEnd, ak.offset + (long) (firstVertex + vertexCount - 1) * ks
                                + (long) ak.size * ak.typeSizeBytes());
                        }
                        rangeStart = Math.max(0, rangeStart);
                        rangeEnd = Math.min(bufferSize, rangeEnd);
                        readOffset = rangeStart;
                        readSize = (int) (rangeEnd - rangeStart);
                    } else {
                        readOffset = 0;
                        readSize = bufferSize;
                    }

                    if (readSize <= 0) continue;

                    final ByteBuffer buf = MemoryUtilities.memAlloc(readSize);
                    GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, readOffset, buf);
                    scratchAttribBuffers[i] = buf;
                    scratchAttribBaseOffsets[i] = readOffset;
                    scratchVBOIds[readVBOCount] = a.vboId;
                    scratchVBOReadOffsets[readVBOCount] = readOffset;
                    scratchVBOBuffers[readVBOCount] = buf;
                    readVBOCount++;
                }
            } else if (a.clientPointer != null) {
                // Client-memory attrib — use pointer directly (no allocation needed)
                scratchAttribBuffers[i] = a.clientPointer;
            }
        }

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);

        return readVBOCount;
    }

    private static void freeReadbackBuffers(int readVBOCount) {
        for (int i = 0; i < readVBOCount; i++) {
            MemoryUtilities.memFree(scratchVBOBuffers[i]);
            scratchVBOBuffers[i] = null;
        }
    }

    private static void prepareReaders() {
        nonPositionCount = 0;
        positionReaderIndex = -1;

        for (int i = 0; i < NUM_ATTRIBS; i++) {
            final ByteBuffer buf = scratchAttribBuffers[i];
            if (buf == null) continue;
            final VertexAttribState.Attrib a = VertexAttribState.get(i);

            final AttribReader r = readers[i];
            r.data = buf;
            r.stride = a.effectiveStride();
            r.baseOffset = (int) (a.offset - scratchAttribBaseOffsets[i]);
            r.size = a.size;
            r.loc = i;
            r.attrib = a;

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

    private static DirectTessellator readVerticesFromBufferIndexed(int mode, ByteBuffer eboData, int indexType, int eboOffset, int indicesCount) {
        begin(mode);
        final DirectTessellator t = tessellator();

        for (int i = 0; i < indicesCount; i++) {
            final int index = readIndex(eboData, indexType, eboOffset, i);
            readVertexFromBuffer(t, index);
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
                r.attrib.readComponent(r.data, base, 0),
                r.attrib.readComponent(r.data, base, 1),
                (r.size >= 3) ? r.attrib.readComponent(r.data, base, 2) : 0.0f);
        }
    }

    private static void emitAttrib(DirectTessellator t, AttribReader r, int base) {
        if (r.loc == LOC_NORMAL) {
            t.setNormal(
                r.attrib.readComponent(r.data, base, 0),
                r.attrib.readComponent(r.data, base, 1),
                r.attrib.readComponent(r.data, base, 2));
        } else if (r.loc == LOC_UV0) {
            t.setTextureUV(
                r.attrib.readComponent(r.data, base, 0),
                (r.size >= 2) ? r.attrib.readComponent(r.data, base, 1) : 0.0f);
        } else if (r.loc == LOC_UV1) {
            if (r.attrib.type == GL11.GL_SHORT) {
                final int s = r.data.getShort(base) & 0xFFFF;
                final int u = (r.size >= 2) ? (r.data.getShort(base + 2) & 0xFFFF) : 0;
                t.setBrightness((u << 16) | s);
            } else if (r.attrib.type == GL11.GL_FLOAT) {
                final int s = (int) r.data.getFloat(base);
                final int u = (r.size >= 2) ? (int) r.data.getFloat(base + 4) : 0;
                t.setBrightness((u << 16) | (s & 0xFFFF));
            }
        } else if (r.loc == LOC_COLOR) {
            final float cr = r.attrib.readComponent(r.data, base, 0);
            final float cg = r.attrib.readComponent(r.data, base, 1);
            final float cb = r.attrib.readComponent(r.data, base, 2);
            final float ca = (r.size >= 4) ? r.attrib.readComponent(r.data, base, 3) : 1.0f;
            t.setColorRGBA((int) (cr * 255.0f), (int) (cg * 255.0f), (int) (cb * 255.0f), (int) (ca * 255.0f));
        }
    }

}
