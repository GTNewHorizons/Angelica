package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuad;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

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
    private static final Logger LOGGER = LogManager.getLogger("ImmediateModeRecorder");

    // Accumulated quads from completed primitives
    private final List<ModelQuadViewMutable> quads = new ArrayList<>();

    // Accumulated line vertices (pairs of vertices for GL_LINES)
    private final List<LineVertex> lineVertices = new ArrayList<>();

    // Current primitive state
    private int currentPrimitiveType = -1;
    private boolean inPrimitive = false;

    // Vertices for current primitive (cleared on glEnd)
    private final List<ImmediateVertex> currentVertices = new ArrayList<>();

    // Current immediate mode state (set by glTexCoord*, glNormal*)
    private float texCoordS = 0.0f;
    private float texCoordT = 0.0f;
    private float normalX = 0.0f;
    private float normalY = 0.0f;
    private float normalZ = 1.0f;  // Default normal points +Z

    // Track which attributes have been set (for flags)
    private boolean hasTexCoord = false;
    private boolean hasNormal = false;
    private boolean hasColor = false;

    /**
     * Represents a single vertex captured during immediate mode.
     */
    private static class ImmediateVertex {
        float x, y, z;           // Position
        int color;               // Packed ABGR color
        float texU, texV;        // Texture coordinates
        int normal;              // Packed normal

        ImmediateVertex(float x, float y, float z, int color, float texU, float texV, int normal) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.texU = texU;
            this.texV = texV;
            this.normal = normal;
        }
    }

    /**
     * Represents a vertex for line primitives.
     * Simpler than ImmediateVertex - just position and color.
     */
    public static class LineVertex {
        public final float x, y, z;
        public final int color;  // Packed ABGR color

        public LineVertex(float x, float y, float z, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }
    }

    public ImmediateModeRecorder() {
        // Recorder is ready to capture geometry
    }

    /**
     * Set the current texture coordinate.
     * Called by GLStateManager.glTexCoord*.
     */
    public void setTexCoord(float s, float t) {
        this.texCoordS = s;
        this.texCoordT = t;
        this.hasTexCoord = true;
    }

    /**
     * Set the current normal vector.
     * Called by GLStateManager.glNormal*.
     */
    public void setNormal(float x, float y, float z) {
        this.normalX = x;
        this.normalY = y;
        this.normalZ = z;
        this.hasNormal = true;
    }

    /**
     * Check if currently in a primitive (between glBegin and glEnd).
     */
    public boolean isInPrimitive() {
        return inPrimitive;
    }

    /**
     * Start recording a new primitive.
     */
    public void begin(int primitiveType) {
        if (inPrimitive) {
            throw new IllegalStateException("glBegin called while already in primitive");
        }
        this.currentPrimitiveType = primitiveType;
        this.currentVertices.clear();
        this.inPrimitive = true;
    }

    /**
     * End the current primitive, convert to quads/lines, and return them immediately.
     * This allows the caller to create AccumulatedDraw at the correct command position.
     *
     * @return Result containing quads, lines, and flags, or null if no geometry was produced
     */
    public Result end() {
        if (!inPrimitive) {
            throw new IllegalStateException("glEnd called without glBegin");
        }

        // Convert this primitive's vertices to quads or lines
        convertPrimitiveToQuads();
        this.currentVertices.clear();
        this.inPrimitive = false;

        // Return geometry from this primitive immediately (don't accumulate)
        if (quads.isEmpty() && lineVertices.isEmpty()) {
            return null;
        }

        // Copy geometry and create flags
        List<ModelQuadViewMutable> resultQuads = new ArrayList<>(quads);
        List<LineVertex> resultLines = new ArrayList<>(lineVertices);
        CapturingTessellator.Flags flags = new CapturingTessellator.Flags(
            hasTexCoord,   // hasTexture
            false,         // hasBrightness - immediate mode doesn't typically use lightmap
            hasColor,      // hasColor
            hasNormal      // hasNormals
        );

        // Clear for next primitive (but keep attribute tracking state)
        quads.clear();
        lineVertices.clear();

        return new Result(resultQuads, resultLines, flags);
    }

    /**
     * Record a vertex with current attributes.
     * Color is read from GLStateManager at call time.
     */
    public void vertex(float x, float y, float z) {
        if (!inPrimitive) {
            throw new IllegalStateException("glVertex called outside glBegin/glEnd");
        }

        // Get current color from GLStateManager
        int packedColor = packColor(
            GLStateManager.getColor().getRed(),
            GLStateManager.getColor().getGreen(),
            GLStateManager.getColor().getBlue(),
            GLStateManager.getColor().getAlpha()
        );
        if (packedColor != 0xFFFFFFFF) {
            hasColor = true;
        }

        // Pack normal
        int packedNormal = NormI8.pack(normalX, normalY, normalZ);

        currentVertices.add(new ImmediateVertex(
            x, y, z,
            packedColor,
            texCoordS, texCoordT,
            packedNormal
        ));
    }

    /**
     * Pack RGBA floats (0-1) into ABGR int format used by ModelQuad.
     */
    private static int packColor(float r, float g, float b, float a) {
        int ri = (int) (r * 255.0f) & 0xFF;
        int gi = (int) (g * 255.0f) & 0xFF;
        int bi = (int) (b * 255.0f) & 0xFF;
        int ai = (int) (a * 255.0f) & 0xFF;
        // ABGR format (little-endian: stored as RGBA bytes)
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }

    /**
     * Convert the current primitive vertices to quads.
     * Handles different primitive types.
     */
    private void convertPrimitiveToQuads() {
        int vertexCount = currentVertices.size();
        if (vertexCount == 0) {
            return;
        }

        switch (currentPrimitiveType) {
            case GL11.GL_QUADS -> convertQuads();
            case GL11.GL_TRIANGLES -> convertTriangles();
            case GL11.GL_TRIANGLE_FAN, GL11.GL_POLYGON -> convertTriangleFan();
            case GL11.GL_TRIANGLE_STRIP -> convertTriangleStrip();
            case GL11.GL_QUAD_STRIP -> convertQuadStrip();
            case GL11.GL_LINES -> convertLines();
            case GL11.GL_LINE_STRIP -> convertLineStripToLines();
            case GL11.GL_LINE_LOOP -> convertLineLoopToLines();
            case GL11.GL_POINTS -> {
                LOGGER.warn("[ImmediateModeRecorder] Point primitives not supported, discarding {} vertices", vertexCount);
            }
            default -> {
                LOGGER.warn("[ImmediateModeRecorder] Unknown primitive type {}, discarding {} vertices",
                    currentPrimitiveType, vertexCount);
            }
        }
    }

    /**
     * Convert GL_QUADS vertices to ModelQuads (4 vertices per quad).
     */
    private void convertQuads() {
        int vertexCount = currentVertices.size();
        int fullQuads = vertexCount / 4;

        for (int i = 0; i < fullQuads; i++) {
            int base = i * 4;
            addQuad(
                currentVertices.get(base),
                currentVertices.get(base + 1),
                currentVertices.get(base + 2),
                currentVertices.get(base + 3)
            );
        }

        int remaining = vertexCount % 4;
        if (remaining > 0) {
            LOGGER.warn("[ImmediateModeRecorder] GL_QUADS: Incomplete quad, discarding {} vertices", remaining);
        }
    }

    /**
     * Convert GL_TRIANGLES vertices to degenerate quads (3 vertices per triangle, duplicate last).
     */
    private void convertTriangles() {
        int vertexCount = currentVertices.size();
        int fullTriangles = vertexCount / 3;

        for (int i = 0; i < fullTriangles; i++) {
            int base = i * 3;
            ImmediateVertex v0 = currentVertices.get(base);
            ImmediateVertex v1 = currentVertices.get(base + 1);
            ImmediateVertex v2 = currentVertices.get(base + 2);
            // Degenerate quad: duplicate third vertex
            addQuad(v0, v1, v2, v2);
        }

        int remaining = vertexCount % 3;
        if (remaining > 0) {
            LOGGER.warn("[ImmediateModeRecorder] GL_TRIANGLES: Incomplete triangle, discarding {} vertices", remaining);
        }
    }

    /**
     * Convert GL_TRIANGLE_FAN vertices to triangles then to degenerate quads.
     * First vertex is center, subsequent vertices form fan.
     */
    private void convertTriangleFan() {
        int vertexCount = currentVertices.size();
        if (vertexCount < 3) {
            LOGGER.warn("[ImmediateModeRecorder] GL_TRIANGLE_FAN: Need at least 3 vertices, got {}", vertexCount);
            return;
        }

        ImmediateVertex center = currentVertices.get(0);
        for (int i = 1; i < vertexCount - 1; i++) {
            ImmediateVertex v1 = currentVertices.get(i);
            ImmediateVertex v2 = currentVertices.get(i + 1);
            // Triangle: center, v1, v2 -> degenerate quad
            addQuad(center, v1, v2, v2);
        }
    }

    /**
     * Convert GL_TRIANGLE_STRIP vertices to triangles then to degenerate quads.
     */
    private void convertTriangleStrip() {
        int vertexCount = currentVertices.size();
        if (vertexCount < 3) {
            LOGGER.warn("[ImmediateModeRecorder] GL_TRIANGLE_STRIP: Need at least 3 vertices, got {}", vertexCount);
            return;
        }

        for (int i = 0; i < vertexCount - 2; i++) {
            ImmediateVertex v0, v1, v2;
            if ((i & 1) == 0) {
                // Even triangle: v[i], v[i+1], v[i+2]
                v0 = currentVertices.get(i);
                v1 = currentVertices.get(i + 1);
                v2 = currentVertices.get(i + 2);
            } else {
                // Odd triangle: v[i+1], v[i], v[i+2] (reverse winding)
                v0 = currentVertices.get(i + 1);
                v1 = currentVertices.get(i);
                v2 = currentVertices.get(i + 2);
            }
            addQuad(v0, v1, v2, v2);
        }
    }

    /**
     * Convert GL_QUAD_STRIP vertices to quads.
     * Vertices come in pairs: (v0,v1), (v2,v3), (v4,v5)...
     * Each quad uses 4 consecutive vertices in pattern: v[2i], v[2i+1], v[2i+3], v[2i+2]
     */
    private void convertQuadStrip() {
        int vertexCount = currentVertices.size();
        if (vertexCount < 4) {
            LOGGER.warn("[ImmediateModeRecorder] GL_QUAD_STRIP: Need at least 4 vertices, got {}", vertexCount);
            return;
        }

        int numQuads = (vertexCount - 2) / 2;
        for (int i = 0; i < numQuads; i++) {
            int base = i * 2;
            // Quad strip winding: v[0], v[1], v[3], v[2]
            addQuad(
                currentVertices.get(base),
                currentVertices.get(base + 1),
                currentVertices.get(base + 3),
                currentVertices.get(base + 2)
            );
        }
    }

    /**
     * Convert GL_LINES vertices to line segments (pairs of vertices).
     */
    private void convertLines() {
        int vertexCount = currentVertices.size();
        int fullLines = vertexCount / 2;

        for (int i = 0; i < fullLines; i++) {
            int base = i * 2;
            ImmediateVertex v0 = currentVertices.get(base);
            ImmediateVertex v1 = currentVertices.get(base + 1);
            lineVertices.add(new LineVertex(v0.x, v0.y, v0.z, v0.color));
            lineVertices.add(new LineVertex(v1.x, v1.y, v1.z, v1.color));
        }

        int remaining = vertexCount % 2;
        if (remaining > 0) {
            LOGGER.warn("[ImmediateModeRecorder] GL_LINES: Incomplete line, discarding {} vertices", remaining);
        }
    }

    /**
     * Convert GL_LINE_STRIP to individual line segments.
     * Vertices 0-1-2-3 become lines: 0-1, 1-2, 2-3
     */
    private void convertLineStripToLines() {
        int vertexCount = currentVertices.size();
        if (vertexCount < 2) {
            LOGGER.warn("[ImmediateModeRecorder] GL_LINE_STRIP: Need at least 2 vertices, got {}", vertexCount);
            return;
        }

        for (int i = 0; i < vertexCount - 1; i++) {
            ImmediateVertex v0 = currentVertices.get(i);
            ImmediateVertex v1 = currentVertices.get(i + 1);
            lineVertices.add(new LineVertex(v0.x, v0.y, v0.z, v0.color));
            lineVertices.add(new LineVertex(v1.x, v1.y, v1.z, v1.color));
        }
    }

    /**
     * Convert GL_LINE_LOOP to individual line segments.
     * Like LINE_STRIP but also closes the loop from last to first vertex.
     */
    private void convertLineLoopToLines() {
        int vertexCount = currentVertices.size();
        if (vertexCount < 2) {
            LOGGER.warn("[ImmediateModeRecorder] GL_LINE_LOOP: Need at least 2 vertices, got {}", vertexCount);
            return;
        }

        // Convert as strip first
        convertLineStripToLines();

        // Close the loop: add last-to-first segment
        ImmediateVertex vLast = currentVertices.get(vertexCount - 1);
        ImmediateVertex vFirst = currentVertices.get(0);
        lineVertices.add(new LineVertex(vLast.x, vLast.y, vLast.z, vLast.color));
        lineVertices.add(new LineVertex(vFirst.x, vFirst.y, vFirst.z, vFirst.color));
    }

    /**
     * Create a ModelQuad from 4 ImmediateVertex objects.
     */
    private void addQuad(ImmediateVertex v0, ImmediateVertex v1, ImmediateVertex v2, ImmediateVertex v3) {
        ModelQuad quad = new ModelQuad();

        setVertex(quad, 0, v0);
        setVertex(quad, 1, v1);
        setVertex(quad, 2, v2);
        setVertex(quad, 3, v3);

        // Set light face based on computed normal
        quad.setLightFace(ModelQuadUtil.findLightFace(quad.getComputedFaceNormal()));

        quads.add(quad);
    }

    /**
     * Set a single vertex in a ModelQuad.
     */
    private void setVertex(ModelQuad quad, int idx, ImmediateVertex v) {
        quad.setX(idx, v.x);
        quad.setY(idx, v.y);
        quad.setZ(idx, v.z);
        quad.setColor(idx, v.color);
        quad.setTexU(idx, v.texU);
        quad.setTexV(idx, v.texV);
        quad.setForgeNormal(idx, v.normal);
        // Light defaults to full brightness (no lightmap for immediate mode typically)
        quad.setLight(idx, ModelQuadUtil.DEFAULT_LIGHTMAP);
    }

    /**
     * Check if any immediate mode geometry has been captured.
     *
     * <p>Note: With immediate flush mode, this will typically return false as geometry
     * is returned from end() immediately. This method is kept for edge cases
     * where primitives might fail to convert (e.g., incomplete quads).
     */
    public boolean hasGeometry() {
        return !quads.isEmpty() || !lineVertices.isEmpty();
    }

    /**
     * Get any remaining accumulated geometry and clear the recorder.
     *
     * <p>Note: With immediate flush mode, this will typically return null as geometry
     * is returned from end() immediately. This method is kept for cleanup and
     * edge cases where geometry might remain (e.g., if end() was never called).
     *
     * @return Result containing remaining quads, lines, and flags, or null if no geometry
     */
    public Result getQuadsAndClear() {
        if (inPrimitive) {
            throw new IllegalStateException("Cannot get geometry while in primitive (missing glEnd)");
        }

        if (quads.isEmpty() && lineVertices.isEmpty()) {
            return null;
        }

        // Copy geometry and create flags
        List<ModelQuadViewMutable> resultQuads = new ArrayList<>(quads);
        List<LineVertex> resultLines = new ArrayList<>(lineVertices);
        CapturingTessellator.Flags flags = new CapturingTessellator.Flags(
            hasTexCoord,   // hasTexture
            false,         // hasBrightness - immediate mode doesn't typically use lightmap
            hasColor,      // hasColor
            hasNormal      // hasNormals
        );

        // Clear state for reuse
        quads.clear();
        lineVertices.clear();
        hasColor = false;
        hasTexCoord = false;
        hasNormal = false;
        // Note: current texcoord/normal values are NOT reset - they persist per OpenGL spec

        return new Result(resultQuads, resultLines, flags);
    }

    /**
     * Result of immediate mode recording: quads, lines, and their attribute flags.
     */
    @com.github.bsideup.jabel.Desugar
    public record Result(List<ModelQuadViewMutable> quads, List<LineVertex> lines, CapturingTessellator.Flags flags) {}

    /**
     * Reset all state including current texcoord/normal.
     * Called when starting a new display list.
     */
    public void reset() {
        quads.clear();
        lineVertices.clear();
        currentVertices.clear();
        inPrimitive = false;
        currentPrimitiveType = -1;
        texCoordS = 0.0f;
        texCoordT = 0.0f;
        normalX = 0.0f;
        normalY = 0.0f;
        normalZ = 1.0f;
        hasColor = false;
        hasTexCoord = false;
        hasNormal = false;
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
    public Result processDrawArrays(int mode, int first, int count) {
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
    public Result convertClientArrays(
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
