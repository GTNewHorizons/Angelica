package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedLineDraw;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates line geometry during display list compilation.
 * All line draws are collected here, then compiled into a single VBO.
 * <p>
 * Lines all use the same format (POSITION_COLOR), so no format-based grouping needed.
 * <p>
 * Tracks two types of ranges:
 * <ul>
 *   <li><b>mergedRanges</b>: Consecutive same-transform draws merged for optimized path</li>
 *   <li><b>perDrawRanges</b>: One range per input draw for unoptimized path (1:1 with addDraw calls)</li>
 * </ul>
 */
class LineBuffer {
    final List<ImmediateModeRecorder.LineVertex> allVertices = new ArrayList<>();

    // Merged ranges for optimized path (consecutive same-transform draws combined)
    final List<DrawRange> mergedRanges = new ArrayList<>();

    // Per-draw ranges for unoptimized path (1:1 with input draws)
    final List<DrawRange> perDrawRanges = new ArrayList<>();

    int currentVertexOffset = 0;

    // Track the current range being built for merging consecutive same-transform draws
    private int pendingStartVertex = -1;
    private int pendingVertexCount = 0;
    private Matrix4f pendingTransform = null;
    private int pendingCommandIndex = -1;

    /**
     * Add a line draw to this buffer.
     * Tracks both merged (for optimized) and per-draw (for unoptimized) ranges.
     */
    void addDraw(AccumulatedLineDraw draw) {
        int vertexCount = draw.lines.size();  // Each LineVertex is one vertex, lines come in pairs

        // Always track per-draw range for unoptimized path
        perDrawRanges.add(new DrawRange(currentVertexOffset, vertexCount, draw.transform, draw.commandIndex));

        // Merge logic for optimized path
        if (pendingTransform != null && pendingTransform.equals(draw.transform)) {
            // Extend the pending range
            pendingVertexCount += vertexCount;
        } else {
            // Flush pending range if any
            flushPendingRange();

            // Start new pending range
            pendingStartVertex = currentVertexOffset;
            pendingVertexCount = vertexCount;
            pendingTransform = new Matrix4f(draw.transform);
            pendingCommandIndex = draw.commandIndex;
        }

        allVertices.addAll(draw.lines);
        currentVertexOffset += vertexCount;
    }

    private void flushPendingRange() {
        if (pendingTransform != null) {
            mergedRanges.add(new DrawRange(pendingStartVertex, pendingVertexCount, pendingTransform, pendingCommandIndex));
            pendingTransform = null;
        }
    }

    /**
     * Compile all accumulated line vertices into a single VBO.
     * @return The compiled buffer with VBO and both range types, or null if empty
     */
    CompiledLineBuffer finish() {
        if (allVertices.isEmpty()) {
            return null;
        }

        // Flush any remaining pending range
        flushPendingRange();

        VertexBuffer vbo = compileLinesToVBO(allVertices);
        return new CompiledLineBuffer(
            vbo,
            mergedRanges.toArray(new DrawRange[0]),
            perDrawRanges.toArray(new DrawRange[0])
        );
    }

    /**
     * Compile line vertices into a VertexBuffer (VAO or VBO).
     * Lines use POSITION_COLOR format (x, y, z, color).
     */
    private static VertexBuffer compileLinesToVBO(List<ImmediateModeRecorder.LineVertex> lines) {
        final VertexFormat format = DefaultVertexFormat.POSITION_COLOR;
        final VertexBuffer vao = VAOManager.createVAO(format, GL11.GL_LINES);

        // POSITION_COLOR format: 3 floats (12 bytes) + 4 bytes color = 16 bytes per vertex
        final int vertexSize = format.getVertexSize();
        final ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(vertexSize * lines.size());

        for (ImmediateModeRecorder.LineVertex v : lines) {
            buffer.putFloat(v.x);
            buffer.putFloat(v.y);
            buffer.putFloat(v.z);
            buffer.putInt(v.color);
        }
        buffer.flip();

        vao.upload(buffer);

        return vao;
    }
}
