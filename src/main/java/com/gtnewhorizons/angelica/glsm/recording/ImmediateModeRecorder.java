package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Records immediate mode GL calls (glBegin/glEnd/glVertex) during display list compilation.
 * Converts immediate mode geometry to ModelQuad format for VBO compilation.
 *
 * <p>Tracks current immediate mode state:
 * <ul>
 *   <li>TexCoord: Set by glTexCoord* calls</li>
 *   <li>Normal: Set by glNormal* calls</li>
 *   <li>Color: Read from GLStateManager.getColor() at vertex emission time</li>
 * </ul>
 *
 * <p>Supported primitive types:
 * <ul>
 *   <li>GL_QUADS: 4 vertices per quad (native support)</li>
 *   <li>GL_TRIANGLES: 3 vertices per triangle (converted to degenerate quad)</li>
 *   <li>GL_TRIANGLE_FAN: Fan of triangles (converted to individual quads)</li>
 *   <li>GL_TRIANGLE_STRIP: Strip of triangles (converted to individual quads)</li>
 *   <li>GL_QUAD_STRIP: Strip of quads (converted to individual quads)</li>
 *   <li>GL_POLYGON: Polygon (treated as triangle fan)</li>
 * </ul>
 *
 * <p>Unsupported primitive types (logged warning, geometry discarded):
 * <ul>
 *   <li>GL_LINES, GL_LINE_STRIP, GL_LINE_LOOP: Line primitives</li>
 *   <li>GL_POINTS: Point primitives</li>
 * </ul>
 */
public class ImmediateModeRecorder {
    private final DirectTessellator tessellator = new DirectTessellator(BufferUtils.createByteBuffer(0x200000));

    public ImmediateModeRecorder() {
        // Recorder is ready to capture geometry
    }

    /**
     * Set the current texture coordinate.
     * Called by GLStateManager.glTexCoord*.
     */
    public void setTexCoord(float s, float t) {
        tessellator.setTextureUV(s, t);
    }

    /**
     * Set the current normal vector.
     * Called by GLStateManager.glNormal*.
     */
    public void setNormal(float x, float y, float z) {
        tessellator.setNormal(x, y, z);
    }

    /**
     * Start recording a new primitive.
     */
    public void begin(int primitiveType) {
        tessellator.startDrawing(primitiveType);
    }

    /**
     * End the current primitive, convert to quads/lines, and return them immediately.
     * This allows the caller to create AccumulatedDraw at the correct command position.
     *
     * @return Result containing quads, lines, and flags, or null if no geometry was produced
     */
    public DirectTessellator end() { //TODO
        if (!tessellator.isDrawing) {
            throw new IllegalStateException("glEnd called without glBegin");
        }

        tessellator.isDrawing = false;

        if (tessellator.isEmpty()) return null;

        return this.tessellator;
    }

    /**
     * Record a vertex with current attributes.
     * Color is read from GLStateManager at call time.
     */
    public void vertex(float x, float y, float z) {
        if (!tessellator.isDrawing) {
            throw new IllegalStateException("glVertex called outside glBegin/glEnd");
        }
        tessellator.addVertex(x, y, z);
    }

    /**
     * Check if any immediate mode geometry has been captured.
     *
     * <p>Note: With immediate flush mode, this will typically return false as geometry
     * is returned from end() immediately. This method is kept for edge cases
     * where primitives might fail to convert (e.g., incomplete quads).
     */
    public boolean hasGeometry() {
        return tessellator.rawBufferIndex != 0;
    }

    /**
     * Reset all state including current texcoord/normal.
     * Called when starting a new display list.
     */
    public void reset() {
        tessellator.reset();
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
    public DirectTessellator processDrawArrays(int mode, int first, int count) {
        final var cas = GLStateManager.getClientArrayState();
        final var vertexPointer = cas.isVertexArrayEnabled() ? cas.getVertexPointer() : null;
        final var colorPointer = cas.isColorArrayEnabled() ? cas.getColorPointer() : null;

        return convertClientArrays(
            mode, first, count,
            vertexPointer,
            cas.getVertexPointerType(), cas.getVertexPointerSize(), cas.getVertexPointerStride(),
            colorPointer,
            cas.getColorPointerType(), cas.getColorPointerSize(), cas.getColorPointerStride()
        );
    }

    /**
     * Convert client array data to immediate mode.
     * Called by glDrawArrays during display list recording.
     * Like Mesa's save_DrawArrays, reads vertex data from arrays and emits as immediate mode.
     *
     * @param mode Primitive type (GL_QUADS, GL_LINES, etc.)
     * @param first Starting index in arrays
     * @param count Number of vertices
     * @param vertexPointer Vertex position buffer (or null if not enabled)
     * @param vertexType GL type (GL_FLOAT, GL_DOUBLE, etc.)
     * @param vertexSize Size of vertex (2, 3, or 4)
     * @param vertexStride Stride in bytes (0 for tightly packed)
     * @param colorPointer Color buffer (or null if not enabled)
     * @param colorType GL type (GL_FLOAT, GL_UNSIGNED_BYTE, etc.)
     * @param colorSize Size of color (3 or 4)
     * @param colorStride Stride in bytes (0 for tightly packed)
     * @return Result containing converted geometry, or null if no geometry produced
     */
    public DirectTessellator convertClientArrays(
        int mode, int first, int count,
        java.nio.Buffer vertexPointer, int vertexType, int vertexSize, int vertexStride,
        java.nio.Buffer colorPointer, int colorType, int colorSize, int colorStride
    ) {
        begin(mode);

        // Hoist type checks and stride calculations outside the loop for performance.
        // Mesa's algorithm: offset_bytes = index * effective_stride
        // effective_stride = (stride == 0) ? (numComponents * sizeof(element)) : stride

        // Determine color reader (hoisted out of loop)
        final ColorReader colorReader;
        final int colorEffectiveStride;
        if (colorPointer == null) {
            colorReader = null;
            colorEffectiveStride = 0;
        } else if (colorType == GL11.GL_FLOAT && colorPointer instanceof FloatBuffer fb) {
            colorEffectiveStride = (colorStride == 0) ? colorSize * 4 : colorStride;
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
            colorEffectiveStride = (colorStride == 0) ? colorSize : colorStride;
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
            colorEffectiveStride = 0;
            colorReader = (idx) -> GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Determine vertex reader (hoisted out of loop)
        final VertexReader vertexReader;
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
                vertex(x, y, z);
            };
        } else if (vertexType == GL11.GL_DOUBLE && vertexPointer instanceof DoubleBuffer db) {
            final int effectiveStride = (vertexStride == 0) ? vertexSize * 8 : vertexStride;
            final int strideInDoubles = effectiveStride / 8;
            vertexReader = (idx) -> {
                final int offset = idx * strideInDoubles;
                final float x = (float) db.get(offset);
                final float y = (float) db.get(offset + 1);
                final float z = (vertexSize >= 3) ? (float) db.get(offset + 2) : 0.0f;
                vertex(x, y, z);
            };
        } else if (vertexType == GL11.GL_INT && vertexPointer instanceof IntBuffer ib) {
            final int effectiveStride = (vertexStride == 0) ? vertexSize * 4 : vertexStride;
            final int strideInInts = effectiveStride / 4;
            vertexReader = (idx) -> {
                final int offset = idx * strideInInts;
                final float x = ib.get(offset);
                final float y = ib.get(offset + 1);
                final float z = (vertexSize >= 3) ? ib.get(offset + 2) : 0.0f;
                vertex(x, y, z);
            };
        } else if (vertexType == GL11.GL_SHORT && vertexPointer instanceof ShortBuffer sb) {
            final int effectiveStride = (vertexStride == 0) ? vertexSize * 2 : vertexStride;
            final int strideInShorts = effectiveStride / 2;
            vertexReader = (idx) -> {
                final int offset = idx * strideInShorts;
                final float x = sb.get(offset);
                final float y = sb.get(offset + 1);
                final float z = (vertexSize >= 3) ? sb.get(offset + 2) : 0.0f;
                vertex(x, y, z);
            };
        } else {
            // Unsupported vertex type
            vertexReader = null;
        }

        // Main loop - no type checks, just function calls
        for (int i = first; i < first + count; i++) {
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
    private interface ColorReader {
        void read(int index);
    }

    @FunctionalInterface
    private interface VertexReader {
        void read(int index);
    }
}
