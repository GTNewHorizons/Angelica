package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LineQuadsTest {

    private static final int RED = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFFFF0000;

    private static ByteBuffer positionColor(float[][] positions, int[] colors) {
        final ByteBuffer buf = ByteBuffer.allocateDirect(positions.length * 16).order(ByteOrder.nativeOrder());
        for (int i = 0; i < positions.length; i++) {
            buf.putFloat(positions[i][0]).putFloat(positions[i][1]).putFloat(positions[i][2]).putInt(colors[i]);
        }
        return buf.flip();
    }

    private static ByteBuffer allocateQuads(int drawMode, int vertexCount) {
        final VertexFormat quadFormat = LineQuads.quadFormat(DefaultVertexFormat.POSITION_COLOR);
        final int quadVertices = LineQuads.segmentCount(drawMode, vertexCount) * LineQuads.VERTICES_PER_SEGMENT;
        return ByteBuffer.allocateDirect(quadVertices * quadFormat.getVertexSize()).order(ByteOrder.nativeOrder());
    }

    private static ByteBuffer expand(ByteBuffer src, int drawMode, int vertexCount) {
        final ByteBuffer dst = allocateQuads(drawMode, vertexCount);
        LineQuads.expandWithDirection(memAddress0(src), DefaultVertexFormat.POSITION_COLOR, drawMode, vertexCount, memAddress0(dst));
        return dst;
    }

    @Test
    void otherEndCornersCarryTheOppositeEndpoint() {
        final ByteBuffer src = positionColor(new float[][] {{0, 0, 0}, {2, 0, 3}}, new int[] {RED, GREEN});
        final ByteBuffer quads = allocateQuads(GL11.GL_LINES, 2);
        LineQuads.expandWithOtherEnd(memAddress0(src), DefaultVertexFormat.POSITION_COLOR, GL11.GL_LINES, 2, memAddress0(quads));

        final float[] end = {2, 0, 3};
        final float[] start = {0, 0, 0};
        final float[] ownX = {0, 0, 2, 0, 2, 2};
        final int[] color = {RED, RED, GREEN, RED, GREEN, GREEN};
        for (int i = 0; i < 6; i++) {
            final boolean isStart = i == 0 || i == 1 || i == 3;
            assertVertex(quads, i, ownX[i], isStart ? end : start, color[i]);
        }
    }

    private static final int STRIDE = 28;

    private static void assertVertex(ByteBuffer quads, int index, float x, float[] direction, int color) {
        final int base = index * STRIDE;
        assertEquals(x, quads.getFloat(base), "x of vertex " + index);
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(direction[axis], quads.getFloat(base + 12 + axis * 4), "direction of vertex " + index);
        }
        assertEquals(color, quads.getInt(base + 24), "color of vertex " + index);
    }

    @Test
    void stripSegmentsFollowTheGeometryShaderCornerOrder() {
        assertEquals(STRIDE, LineQuads.quadFormat(DefaultVertexFormat.POSITION_COLOR).getVertexSize());

        final ByteBuffer src = positionColor(new float[][] {{0, 0, 0}, {2, 0, 0}, {2, 0, 3}}, new int[] {RED, GREEN, BLUE});
        final ByteBuffer quads = expand(src, GL11.GL_LINE_STRIP, 3);

        final float[] alongX = {1, 0, 0};
        final float[] alongZ = {0, 0, 1};
        final float[] firstX = {0, 0, 2, 0, 2, 2};
        final int[] firstColor = {RED, RED, GREEN, RED, GREEN, GREEN};
        for (int i = 0; i < 6; i++) {
            assertVertex(quads, i, firstX[i], alongX, firstColor[i]);
        }
        final int[] secondColor = {GREEN, GREEN, BLUE, GREEN, BLUE, BLUE};
        for (int i = 0; i < 6; i++) {
            assertVertex(quads, 6 + i, 2, alongZ, secondColor[i]);
        }
    }

    @Test
    void loopClosesBackToFirstVertex() {
        final ByteBuffer src = positionColor(new float[][] {{0, 0, 0}, {2, 0, 0}, {2, 0, 3}}, new int[] {RED, GREEN, BLUE});
        assertEquals(3, LineQuads.segmentCount(GL11.GL_LINE_LOOP, 3));
        final ByteBuffer quads = expand(src, GL11.GL_LINE_LOOP, 3);

        final int[] closingColor = {BLUE, BLUE, RED, BLUE, RED, RED};
        for (int i = 0; i < 6; i++) {
            assertEquals(closingColor[i], quads.getInt((12 + i) * STRIDE + 24), "color of closing vertex " + i);
        }
    }
}
