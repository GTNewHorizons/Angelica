package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.line.ModelLine;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.primitive.ModelPrimitiveView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.tri.ModelTriangle;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedPrimitiveDraw;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates tessellator primitives (lines, triangles) during display list compilation.
 * Similar to FormatBuffer but handles ModelPrimitiveView instances with full 32-byte vertex format.
 * <p>
 * Unlike LineBuffer which handles ImmediateModeRecorder.LineVertex (16-byte: pos+color),
 * this handles ModelLine/ModelTriangle from the tessellator (32-byte: pos+color+tex+light+normal).
 * <p>
 * Primitives are separated by draw mode (GL_LINES, GL_TRIANGLES) into separate VBOs.
 */
class TessellatorPrimitiveBuffer {
    // Use full vertex format: pos(12) + color(4) + tex(8) + light(4) + normal(4) = 32 bytes
    private static final VertexFormat PRIMITIVE_FORMAT = DefaultVertexFormat.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
    private static final int VERTEX_SIZE = PRIMITIVE_FORMAT.getVertexSize(); // 32 bytes

    // Accumulated primitives separated by type
    final List<ModelLine> allLines = new ArrayList<>();
    final List<ModelTriangle> allTriangles = new ArrayList<>();

    // Ranges for lines
    final List<DrawRange> lineMergedRanges = new ArrayList<>();
    final List<DrawRange> linePerDrawRanges = new ArrayList<>();
    int currentLineVertexOffset = 0;
    private int pendingLineStartVertex = -1;
    private int pendingLineVertexCount = 0;
    private Matrix4f pendingLineTransform = null;
    private int pendingLineCommandIndex = -1;

    // Ranges for triangles
    final List<DrawRange> triangleMergedRanges = new ArrayList<>();
    final List<DrawRange> trianglePerDrawRanges = new ArrayList<>();
    int currentTriangleVertexOffset = 0;
    private int pendingTriangleStartVertex = -1;
    private int pendingTriangleVertexCount = 0;
    private Matrix4f pendingTriangleTransform = null;
    private int pendingTriangleCommandIndex = -1;

    /**
     * Add a primitive draw to this buffer.
     * Separates lines and triangles, tracking ranges for both merged and per-draw paths.
     */
    void addDraw(AccumulatedPrimitiveDraw draw) {
        final List<ModelPrimitiveView> primitives = draw.primitives;
        final int size = primitives.size();

        // Single pass: count and collect primitives by type
        int lineCount = 0;
        int triangleCount = 0;
        for (int i = 0; i < size; i++) {
            final ModelPrimitiveView prim = primitives.get(i);
            if (prim instanceof ModelLine ml) {
                allLines.add(ml);
                lineCount++;
            } else if (prim instanceof ModelTriangle mt) {
                allTriangles.add(mt);
                triangleCount++;
            }
        }

        // Process lines
        if (lineCount > 0) {
            final int lineVertexCount = lineCount * 2; // 2 vertices per line
            linePerDrawRanges.add(new DrawRange(currentLineVertexOffset, lineVertexCount, draw.transform, draw.commandIndex));

            // Merge logic for optimized path
            if (pendingLineTransform != null && pendingLineTransform.equals(draw.transform)) {
                pendingLineVertexCount += lineVertexCount;
            } else {
                flushPendingLineRange();
                pendingLineStartVertex = currentLineVertexOffset;
                pendingLineVertexCount = lineVertexCount;
                pendingLineTransform = new Matrix4f(draw.transform);
                pendingLineCommandIndex = draw.commandIndex;
            }
            currentLineVertexOffset += lineVertexCount;
        }

        // Process triangles
        if (triangleCount > 0) {
            final int triangleVertexCount = triangleCount * 3; // 3 vertices per triangle
            trianglePerDrawRanges.add(new DrawRange(currentTriangleVertexOffset, triangleVertexCount, draw.transform, draw.commandIndex));

            // Merge logic for optimized path
            if (pendingTriangleTransform != null && pendingTriangleTransform.equals(draw.transform)) {
                pendingTriangleVertexCount += triangleVertexCount;
            } else {
                flushPendingTriangleRange();
                pendingTriangleStartVertex = currentTriangleVertexOffset;
                pendingTriangleVertexCount = triangleVertexCount;
                pendingTriangleTransform = new Matrix4f(draw.transform);
                pendingTriangleCommandIndex = draw.commandIndex;
            }
            currentTriangleVertexOffset += triangleVertexCount;
        }
    }

    private void flushPendingLineRange() {
        if (pendingLineTransform != null) {
            lineMergedRanges.add(new DrawRange(pendingLineStartVertex, pendingLineVertexCount, pendingLineTransform, pendingLineCommandIndex));
            pendingLineTransform = null;
        }
    }

    private void flushPendingTriangleRange() {
        if (pendingTriangleTransform != null) {
            triangleMergedRanges.add(new DrawRange(pendingTriangleStartVertex, pendingTriangleVertexCount, pendingTriangleTransform, pendingTriangleCommandIndex));
            pendingTriangleTransform = null;
        }
    }

    /**
     * Compile all accumulated primitives into VBOs.
     * @return The compiled buffers with separate VBOs for lines and triangles, or null if empty
     */
    CompiledPrimitiveBuffers finish() {
        flushPendingLineRange();
        flushPendingTriangleRange();

        if (allLines.isEmpty() && allTriangles.isEmpty()) {
            return null;
        }

        final VertexBuffer lineVbo = allLines.isEmpty() ? null : compileLinesToVBO();
        final VertexBuffer triangleVbo = allTriangles.isEmpty() ? null : compileTrianglesToVBO();

        return new CompiledPrimitiveBuffers(
            lineVbo,
            lineMergedRanges.toArray(new DrawRange[0]),
            linePerDrawRanges.toArray(new DrawRange[0]),
            triangleVbo,
            triangleMergedRanges.toArray(new DrawRange[0]),
            trianglePerDrawRanges.toArray(new DrawRange[0])
        );
    }

    private VertexBuffer compileLinesToVBO() {
        final int size = allLines.size();
        final ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(VERTEX_SIZE * size * 2);

        for (int i = 0; i < size; i++) {
            writePrimitiveToBuffer(allLines.get(i), buffer);
        }
        buffer.flip();

        final VertexBuffer vbo = VAOManager.createVAO(PRIMITIVE_FORMAT, GL11.GL_LINES);
        vbo.upload(buffer);
        return vbo;
    }

    private VertexBuffer compileTrianglesToVBO() {
        final int size = allTriangles.size();
        final ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(VERTEX_SIZE * size * 3);

        for (int i = 0; i < size; i++) {
            writePrimitiveToBuffer(allTriangles.get(i), buffer);
        }
        buffer.flip();

        final VertexBuffer vbo = VAOManager.createVAO(PRIMITIVE_FORMAT, GL11.GL_TRIANGLES);
        vbo.upload(buffer);
        return vbo;
    }

    /**
     * Write a primitive's vertices to the buffer.
     * Layout: pos(12) + color(4) + tex(8) + light(4) + normal(4) = 32 bytes per vertex
     */
    private static void writePrimitiveToBuffer(ModelPrimitiveView prim, ByteBuffer buffer) {
        final int vertexCount = prim.getVertexCount();
        for (int i = 0; i < vertexCount; i++) {
            // Position (3 floats = 12 bytes)
            buffer.putFloat(prim.getX(i));
            buffer.putFloat(prim.getY(i));
            buffer.putFloat(prim.getZ(i));

            // Color (4 bytes, ABGR)
            buffer.putInt(prim.getColor(i));

            // Texture (2 floats = 8 bytes)
            buffer.putFloat(prim.getTexU(i));
            buffer.putFloat(prim.getTexV(i));

            // Light (4 bytes, packed)
            buffer.putInt(prim.getLight(i));

            // Normal (4 bytes, packed) - includes 1 byte padding
            buffer.putInt(prim.getForgeNormal(i));
        }
    }
}
