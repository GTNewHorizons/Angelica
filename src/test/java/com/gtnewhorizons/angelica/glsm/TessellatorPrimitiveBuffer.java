package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.line.ModelLine;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.primitive.ModelPrimitiveView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.tri.ModelTriangle;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates tessellator primitives (lines, triangles) for a single vertex format during display list compilation.
 * Similar to FormatBuffer but handles ModelPrimitiveView instances instead of quads.
 * <p>
 * Unlike LineBuffer which handles ImmediateModeRecorder.LineVertex (fixed 16-byte: pos+color),
 * this handles ModelLine/ModelTriangle from the tessellator with format-optimized vertex size.
 * <p>
 * Primitives are separated by draw mode (GL_LINES, GL_TRIANGLES) into separate VBOs.
 */
class TessellatorPrimitiveBuffer {
//    final CapturingTessellator.Flags flags;
//
//    // Accumulated primitives separated by type
//    final List<ModelLine> allLines = new ArrayList<>();
//    final List<ModelTriangle> allTriangles = new ArrayList<>();
//
//    // Ranges for lines
//    final List<DrawRange> lineMergedRanges = new ArrayList<>();
//    final List<DrawRange> linePerDrawRanges = new ArrayList<>();
//    int currentLineVertexOffset = 0;
//    private int pendingLineStartVertex = -1;
//    private int pendingLineVertexCount = 0;
//    private Matrix4f pendingLineTransform = null;
//    private int pendingLineCommandIndex = -1;
//
//    // Ranges for triangles
//    final List<DrawRange> triangleMergedRanges = new ArrayList<>();
//    final List<DrawRange> trianglePerDrawRanges = new ArrayList<>();
//    int currentTriangleVertexOffset = 0;
//    private int pendingTriangleStartVertex = -1;
//    private int pendingTriangleVertexCount = 0;
//    private Matrix4f pendingTriangleTransform = null;
//    private int pendingTriangleCommandIndex = -1;
//
//    TessellatorPrimitiveBuffer(CapturingTessellator.Flags flags) {
//        this.flags = flags;
//    }
//
//    /**
//     * Add a primitive draw to this buffer.
//     * Separates lines and triangles, tracking ranges for both merged and per-draw paths.
//     */
//    void addDraw(AccumulatedPrimitiveDraw draw) {
//        final List<ModelPrimitiveView> primitives = draw.primitives;
//        final int size = primitives.size();
//
//        // Single pass: count and collect primitives by type
//        int lineCount = 0;
//        int triangleCount = 0;
//        for (int i = 0; i < size; i++) {
//            final ModelPrimitiveView prim = primitives.get(i);
//            if (prim instanceof ModelLine ml) {
//                allLines.add(ml);
//                lineCount++;
//            } else if (prim instanceof ModelTriangle mt) {
//                allTriangles.add(mt);
//                triangleCount++;
//            }
//        }
//
//        // Process lines
//        if (lineCount > 0) {
//            final int lineVertexCount = lineCount * 2; // 2 vertices per line
//            linePerDrawRanges.add(new DrawRange(currentLineVertexOffset, lineVertexCount, draw.transform, draw.commandIndex));
//
//            // Merge logic for optimized path
//            if (pendingLineTransform != null && pendingLineTransform.equals(draw.transform)) {
//                pendingLineVertexCount += lineVertexCount;
//            } else {
//                flushPendingLineRange();
//                pendingLineStartVertex = currentLineVertexOffset;
//                pendingLineVertexCount = lineVertexCount;
//                pendingLineTransform = new Matrix4f(draw.transform);
//                pendingLineCommandIndex = draw.commandIndex;
//            }
//            currentLineVertexOffset += lineVertexCount;
//        }
//
//        // Process triangles
//        if (triangleCount > 0) {
//            final int triangleVertexCount = triangleCount * 3; // 3 vertices per triangle
//            trianglePerDrawRanges.add(new DrawRange(currentTriangleVertexOffset, triangleVertexCount, draw.transform, draw.commandIndex));
//
//            // Merge logic for optimized path
//            if (pendingTriangleTransform != null && pendingTriangleTransform.equals(draw.transform)) {
//                pendingTriangleVertexCount += triangleVertexCount;
//            } else {
//                flushPendingTriangleRange();
//                pendingTriangleStartVertex = currentTriangleVertexOffset;
//                pendingTriangleVertexCount = triangleVertexCount;
//                pendingTriangleTransform = new Matrix4f(draw.transform);
//                pendingTriangleCommandIndex = draw.commandIndex;
//            }
//            currentTriangleVertexOffset += triangleVertexCount;
//        }
//    }
//
//    private void flushPendingLineRange() {
//        if (pendingLineTransform != null) {
//            lineMergedRanges.add(new DrawRange(pendingLineStartVertex, pendingLineVertexCount, pendingLineTransform, pendingLineCommandIndex));
//            pendingLineTransform = null;
//        }
//    }
//
//    private void flushPendingTriangleRange() {
//        if (pendingTriangleTransform != null) {
//            triangleMergedRanges.add(new DrawRange(pendingTriangleStartVertex, pendingTriangleVertexCount, pendingTriangleTransform, pendingTriangleCommandIndex));
//            pendingTriangleTransform = null;
//        }
//    }
//
//    /**
//     * Compile all accumulated primitives into VBOs using optimal vertex format.
//     * @return The compiled buffers with separate VBOs for lines and triangles, or null if empty
//     */
//    CompiledPrimitiveBuffers finish() {
//        flushPendingLineRange();
//        flushPendingTriangleRange();
//
//        if (allLines.isEmpty() && allTriangles.isEmpty()) {
//            return null;
//        }
//
//        // Use optimal format based on flags (same logic as quads)
//        final VertexFormat format = DisplayListManager.selectOptimalFormat(flags);
//
//        final VertexBuffer lineVbo = allLines.isEmpty() ? null : compileLinesToVBO(format);
//        final VertexBuffer triangleVbo = allTriangles.isEmpty() ? null : compileTrianglesToVBO(format);
//
//        return new CompiledPrimitiveBuffers(
//            flags,
//            lineVbo,
//            lineMergedRanges.toArray(new DrawRange[0]),
//            linePerDrawRanges.toArray(new DrawRange[0]),
//            triangleVbo,
//            triangleMergedRanges.toArray(new DrawRange[0]),
//            trianglePerDrawRanges.toArray(new DrawRange[0])
//        );
//    }
//
//    private VertexBuffer compileLinesToVBO(VertexFormat format) {
//        final int vertexSize = format.getVertexSize();
//        final int size = allLines.size();
//        final ByteBuffer buffer = BufferUtils.createByteBuffer(vertexSize * size * 2);
//
//        for (int i = 0; i < size; i++) {
//            writePrimitiveToBuffer(allLines.get(i), buffer);
//        }
//        buffer.flip();
//
//        final VertexBuffer vbo = VAOManager.createVAO(format, GL11.GL_LINES);
//        vbo.upload(buffer);
//        return vbo;
//    }
//
//    private VertexBuffer compileTrianglesToVBO(VertexFormat format) {
//        final int vertexSize = format.getVertexSize();
//        final int size = allTriangles.size();
//        final ByteBuffer buffer = BufferUtils.createByteBuffer(vertexSize * size * 3);
//
//        for (int i = 0; i < size; i++) {
//            writePrimitiveToBuffer(allTriangles.get(i), buffer);
//        }
//        buffer.flip();
//
//        final VertexBuffer vbo = VAOManager.createVAO(format, GL11.GL_TRIANGLES);
//        vbo.upload(buffer);
//        return vbo;
//    }
//
//    /**
//     * Write a primitive's vertices to the buffer using the format specified by flags.
//     * Only writes attributes that are present in the format.
//     */
//    private void writePrimitiveToBuffer(ModelPrimitiveView prim, ByteBuffer buffer) {
//        final int vertexCount = prim.getVertexCount();
//        final boolean hasTexture = flags.hasTexture;
//        final boolean hasBrightness = flags.hasBrightness;
//        final boolean hasColor = flags.hasColor;
//        final boolean hasNormals = flags.hasNormals;
//
//        for (int i = 0; i < vertexCount; i++) {
//            // Position (always present) - 12 bytes
//            buffer.putFloat(prim.getX(i));
//            buffer.putFloat(prim.getY(i));
//            buffer.putFloat(prim.getZ(i));
//
//            // Color (4 bytes, ABGR) - if present
//            if (hasColor) {
//                buffer.putInt(prim.getColor(i));
//            }
//
//            // Texture (2 floats = 8 bytes) - if present
//            if (hasTexture) {
//                buffer.putFloat(prim.getTexU(i));
//                buffer.putFloat(prim.getTexV(i));
//            }
//
//            // Light/Brightness (4 bytes, packed) - if present
//            if (hasBrightness) {
//                buffer.putInt(prim.getLight(i));
//            }
//
//            // Normal (4 bytes, packed) - if present
//            if (hasNormals) {
//                buffer.putInt(prim.getForgeNormal(i));
//            }
//        }
//    }
}
