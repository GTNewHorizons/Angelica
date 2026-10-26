package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.debug.render.DebugGeometry;
import com.gtnewhorizons.angelica.debug.render.DebugProgram;
import com.gtnewhorizons.angelica.debug.render.DebugPrograms;
import com.gtnewhorizons.angelica.debug.render.NativeScratch;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import com.gtnewhorizons.angelica.glsm.streaming.LineQuads;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

final class DebugMesh {

    private static final VertexFormat WIDE = LineQuads.quadFormat(DebugMeshData.PLAIN);
    private static final int WIDE_STRIDE = WIDE.getVertexSize();
    private static final DebugMeshData.LineWidth[] WIDTHS = DebugMeshData.LineWidth.values();

    private final int[] lineFirst = new int[DebugMeshData.BUCKET_COUNT];
    private final int[] lineCount = new int[DebugMeshData.BUCKET_COUNT];
    private final boolean[] lineWide = new boolean[DebugMeshData.BUCKET_COUNT];
    private final Matrix4f mvp = new Matrix4f();
    private int plainVao;
    private int plainVbo;
    private int wideVao;
    private int wideVbo;
    private int fillCount;
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    final DebugLabels labels = new DebugLabels();

    void upload(DebugMeshData data, NativeScratch scratch) {
        anchorX = data.anchorX();
        anchorY = data.anchorY();
        anchorZ = data.anchorZ();
        labels.copyFrom(data.labels);

        int plainBytes = data.fillBytes();
        int wideVertices = 0;
        fillCount = data.fillVertexCount();
        for (int b = 0; b < DebugMeshData.BUCKET_COUNT; b++) {
            final int vertices = data.lineVertexCount(b);
            final boolean wide = WIDTHS[b % DebugMeshData.WIDTH_COUNT].pixels > 1.0F;
            lineWide[b] = wide;
            if (vertices == 0) {
                lineFirst[b] = 0;
                lineCount[b] = 0;
            } else if (wide) {
                final int corners = LineQuads.segmentCount(GL11.GL_LINES, vertices) * LineQuads.VERTICES_PER_SEGMENT;
                lineFirst[b] = wideVertices;
                lineCount[b] = corners;
                wideVertices += corners;
            } else {
                lineFirst[b] = plainBytes / DebugMeshData.STRIDE;
                lineCount[b] = vertices;
                plainBytes += data.lineBytes(b);
            }
        }

        if (plainBytes > 0) {
            if (plainVao == 0) {
                plainVbo = GLStateManager.glGenBuffers();
                plainVao = DebugGeometry.createVao(plainVbo, DebugMeshData.PLAIN);
            } else {
                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, plainVbo);
            }
            scratch.ensure(plainBytes);
            long destination = scratch.address();
            final int fillBytes = data.fillBytes();
            if (fillBytes > 0) {
                data.copySection(DebugMeshData.FILL_SECTION, destination);
                destination += fillBytes;
            }
            for (int b = 0; b < DebugMeshData.BUCKET_COUNT; b++) {
                if (lineWide[b] || lineCount[b] == 0) continue;
                data.copySection(DebugMeshData.lineSection(b), destination);
                destination += data.lineBytes(b);
            }
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, scratch.window(plainBytes), GL15.GL_DYNAMIC_DRAW);
        }

        if (wideVertices > 0) {
            final int wideBytes = wideVertices * WIDE_STRIDE;
            scratch.ensure(wideBytes);
            for (int b = 0; b < DebugMeshData.BUCKET_COUNT; b++) {
                if (!lineWide[b] || lineCount[b] == 0) continue;
                LineQuads.expandWithOtherEnd(data.lineAddress(b), DebugMeshData.PLAIN, GL11.GL_LINES, data.lineVertexCount(b), scratch.address() + (long) lineFirst[b] * WIDE_STRIDE);
            }
            if (wideVao == 0) {
                wideVbo = GLStateManager.glGenBuffers();
                wideVao = DebugGeometry.createVao(wideVbo, WIDE);
            } else {
                GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, wideVbo);
            }
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, scratch.window(wideBytes), GL15.GL_DYNAMIC_DRAW);
        }
    }

    static DebugProgram useColor() {
        final DebugProgram color = DebugPrograms.color();
        color.use();
        return color;
    }

    static DebugProgram useLines() {
        final DebugProgram lines = DebugPrograms.lines();
        lines.use();
        final ViewportState viewport = GLStateManager.getViewportState();
        lines.viewport(viewport.width, viewport.height);
        return lines;
    }

    void prepare(Matrix4fc projBase, double camX, double camY, double camZ) {
        mvp.set(projBase).translate((float) (anchorX - camX), (float) (anchorY - camY), (float) (anchorZ - camZ));
    }

    boolean has(boolean wide, boolean onTop) {
        if (!wide && !onTop && fillCount > 0) return true;
        final int from = onTop ? DebugMeshData.WIDTH_COUNT : 0;
        final int to = from + DebugMeshData.WIDTH_COUNT;
        for (int b = from; b < to; b++) {
            if (lineWide[b] == wide && lineCount[b] > 0) return true;
        }
        return false;
    }

    void drawPlain(boolean onTop, DebugProgram color) {
        if (!has(false, onTop)) return;
        color.mvp(mvp);
        GLStateManager.glBindVertexArray(plainVao);
        if (!onTop && fillCount > 0) {
            GLStateManager.enableCull();
            try {
                GLStateManager.glDrawArrays(GL11.GL_QUADS, 0, fillCount);
            } finally {
                GLStateManager.disableCull();
            }
        }
        final int from = onTop ? DebugMeshData.WIDTH_COUNT : 0;
        final int to = from + DebugMeshData.WIDTH_COUNT;
        for (int b = from; b < to; b++) {
            if (lineWide[b] || lineCount[b] == 0) continue;
            GLStateManager.glDrawArrays(GL11.GL_LINES, lineFirst[b], lineCount[b]);
        }
    }

    void drawWide(boolean onTop, DebugProgram lines) {
        if (!has(true, onTop)) return;
        lines.mvp(mvp);
        GLStateManager.glBindVertexArray(wideVao);
        final int from = onTop ? DebugMeshData.WIDTH_COUNT : 0;
        final int to = from + DebugMeshData.WIDTH_COUNT;
        for (int b = from; b < to; b++) {
            if (!lineWide[b] || lineCount[b] == 0) continue;
            lines.lineWidth(WIDTHS[b % DebugMeshData.WIDTH_COUNT].pixels);
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, lineFirst[b], lineCount[b]);
        }
    }

    void modelView(Matrix4f base, double camX, double camY, double camZ, Matrix4f out) {
        out.set(base).translate((float) (anchorX - camX), (float) (anchorY - camY), (float) (anchorZ - camZ));
    }

    void delete() {
        if (plainVbo != 0) GLStateManager.glDeleteBuffers(plainVbo);
        if (plainVao != 0) GLStateManager.glDeleteVertexArrays(plainVao);
        if (wideVbo != 0) GLStateManager.glDeleteBuffers(wideVbo);
        if (wideVao != 0) GLStateManager.glDeleteVertexArrays(wideVao);
        plainVbo = 0;
        plainVao = 0;
        wideVbo = 0;
        wideVao = 0;
        fillCount = 0;
        for (int b = 0; b < DebugMeshData.BUCKET_COUNT; b++) {
            lineFirst[b] = 0;
            lineCount[b] = 0;
            lineWide[b] = false;
        }
        labels.free();
    }
}
