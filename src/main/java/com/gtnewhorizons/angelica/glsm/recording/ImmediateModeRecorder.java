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
     * End the current primitive, convert to quads, and return them immediately.
     * This allows the caller to create AccumulatedDraw at the correct command position.
     *
     * @return Result containing quads and flags, or null if no geometry was produced
     */
    public Result end() {
        if (!inPrimitive) {
            throw new IllegalStateException("glEnd called without glBegin");
        }

        // Convert this primitive's vertices to quads
        convertPrimitiveToQuads();
        this.currentVertices.clear();
        this.inPrimitive = false;

        // Return quads from this primitive immediately (don't accumulate)
        if (quads.isEmpty()) {
            return null;
        }

        // Copy quads and create flags
        List<ModelQuadViewMutable> result = new ArrayList<>(quads);
        CapturingTessellator.Flags flags = new CapturingTessellator.Flags(
            hasTexCoord,   // hasTexture
            false,         // hasBrightness - immediate mode doesn't typically use lightmap
            hasColor,      // hasColor
            hasNormal      // hasNormals
        );

        // Clear quads for next primitive (but keep attribute tracking state)
        quads.clear();

        return new Result(result, flags);
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
            case GL11.GL_LINES, GL11.GL_LINE_STRIP, GL11.GL_LINE_LOOP -> {
                LOGGER.warn("[ImmediateModeRecorder] Line primitives not supported, discarding {} vertices", vertexCount);
            }
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
     * <p>Note: With immediate flush mode, this will typically return false as quads
     * are returned from end() immediately. This method is kept for edge cases
     * where primitives might fail to convert (e.g., incomplete quads).
     */
    public boolean hasGeometry() {
        return !quads.isEmpty();
    }

    /**
     * Get any remaining accumulated quads and clear the recorder.
     *
     * <p>Note: With immediate flush mode, this will typically return null as quads
     * are returned from end() immediately. This method is kept for cleanup and
     * edge cases where geometry might remain (e.g., if end() was never called).
     *
     * @return Result containing remaining quads and flags, or null if no geometry
     */
    public Result getQuadsAndClear() {
        if (inPrimitive) {
            throw new IllegalStateException("Cannot get quads while in primitive (missing glEnd)");
        }

        if (quads.isEmpty()) {
            return null;
        }

        // Copy quads and create flags
        List<ModelQuadViewMutable> result = new ArrayList<>(quads);
        CapturingTessellator.Flags flags = new CapturingTessellator.Flags(
            hasTexCoord,   // hasTexture
            false,         // hasBrightness - immediate mode doesn't typically use lightmap
            hasColor,      // hasColor
            hasNormal      // hasNormals
        );

        // Clear state for reuse
        quads.clear();
        hasColor = false;
        hasTexCoord = false;
        hasNormal = false;
        // Note: current texcoord/normal values are NOT reset - they persist per OpenGL spec

        return new Result(result, flags);
    }

    /**
     * Result of immediate mode recording: quads and their attribute flags.
     */
    @com.github.bsideup.jabel.Desugar
    public record Result(List<ModelQuadViewMutable> quads, CapturingTessellator.Flags flags) {}

    /**
     * Reset all state including current texcoord/normal.
     * Called when starting a new display list.
     */
    public void reset() {
        quads.clear();
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
}
