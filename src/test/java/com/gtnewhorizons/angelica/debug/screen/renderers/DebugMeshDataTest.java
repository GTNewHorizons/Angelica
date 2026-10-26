package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.debug.render.NativeScratch;
import com.gtnewhorizons.angelica.debug.screen.renderers.DebugMeshData.LineWidth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetByte;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetFloat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugMeshDataTest {

    private static final int ANCHOR = 30_000_000;
    private static final int THIN = LineWidth.THIN.ordinal();

    private final DebugMeshData mesh = new DebugMeshData();
    private final DebugMeshData other = new DebugMeshData();

    @AfterEach
    void release() {
        mesh.free();
        other.free();
    }

    private static void emit(DebugMeshData target, double x, LineWidth width) {
        target.begin(ANCHOR, 64, ANCHOR);
        target.quad(ANCHOR, 64, ANCHOR, ANCHOR + 1, 64, ANCHOR, ANCHOR + 1, 65, ANCHOR, ANCHOR, 65, ANCHOR, 0x80FF0000);
        target.line(width, x, 64.5, ANCHOR, ANCHOR + 2, 66, ANCHOR + 3, 0xFF00FF00);
        target.label(ANCHOR + 0.5, 64.25, ANCHOR + 0.5, 7, 0xFFFFFF00);
    }

    @Test
    void farCoordinatesKeepSubBlockPrecision() {
        mesh.begin(ANCHOR, 64, ANCHOR);
        mesh.line(LineWidth.THIN, 30_000_005.002, 64, ANCHOR + 0.002, ANCHOR - 0.3, 64, ANCHOR, -1);
        final long p = mesh.lineAddress(THIN);
        assertEquals(5.002F, memGetFloat(p), 1e-6F);
        assertEquals(0.0F, memGetFloat(p + 4), 0.0F);
        assertEquals(0.002F, memGetFloat(p + 8), 1e-7F);
        assertEquals(-0.3F, memGetFloat(p + 16), 1e-7F);
    }

    @Test
    void identicalEmissionGivesIdenticalHash() {
        emit(mesh, ANCHOR + 0.25, LineWidth.THIN);
        emit(other, ANCHOR + 0.25, LineWidth.THIN);
        assertEquals(mesh.hash(), other.hash());
    }

    private static void emitVariant(DebugMeshData target, int anchorX, double x, LineWidth width, boolean onTop, int color, boolean label) {
        target.begin(anchorX, 0, 0);
        target.alwaysOnTop(onTop);
        target.line(width, x, 0, 0, 1, 1, 1, color);
        if (label) target.label(1, 2, 3, 7, -1);
    }

    static Stream<Arguments> hashVariants() {
        return Stream.of(
            Arguments.of(0, 0.375, LineWidth.THIN, false, 0xFF00FF00, false, "coordinate"),
            Arguments.of(0, 0.25, LineWidth.THIN, false, 0xFF0000FF, false, "color"),
            Arguments.of(0, 0.25, LineWidth.THICK, false, 0xFF00FF00, false, "width bucket"),
            Arguments.of(0, 0.25, LineWidth.THIN, true, 0xFF00FF00, false, "on-top bucket"),
            Arguments.of(0, 0.25, LineWidth.THIN, false, 0xFF00FF00, true, "label"),
            Arguments.of(1, 0.25, LineWidth.THIN, false, 0xFF00FF00, false, "anchor"));
    }

    @ParameterizedTest(name = "{6}")
    @MethodSource("hashVariants")
    void changedEmissionChangesHash(int anchorX, double x, LineWidth width, boolean onTop, int color, boolean label, String what) {
        emitVariant(mesh, 0, 0.25, LineWidth.THIN, false, 0xFF00FF00, false);
        emitVariant(other, anchorX, x, width, onTop, color, label);
        assertNotEquals(mesh.hash(), other.hash());
    }

    @Test
    void alwaysOnTopRoutesLinesOnly() {
        mesh.begin(0, 0, 0);
        mesh.alwaysOnTop(true);
        mesh.line(LineWidth.THIN, 0, 0, 0, 1, 1, 1, -1);
        mesh.quad(0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, -1);
        assertEquals(0, mesh.lineVertexCount(THIN));
        assertEquals(2, mesh.lineVertexCount(THIN + DebugMeshData.WIDTH_COUNT));
        assertEquals(32, mesh.lineBytes(THIN + DebugMeshData.WIDTH_COUNT));
        assertEquals(4, mesh.fillVertexCount());
        mesh.alwaysOnTop(false);
        mesh.line(LineWidth.MEDIUM, 0, 0, 0, 1, 1, 1, -1);
        assertEquals(2, mesh.lineVertexCount(LineWidth.MEDIUM.ordinal()));
        assertEquals(0, mesh.lineVertexCount(LineWidth.MEDIUM.ordinal() + DebugMeshData.WIDTH_COUNT));
    }

    @ParameterizedTest(name = "side {0}")
    @CsvSource({"0,0,-1,0", "1,0,1,0", "2,0,0,-1", "3,0,0,1", "4,-1,0,0", "5,1,0,0"})
    void faceWindsCounterClockwiseSeenFromOutside(int side, int nx, int ny, int nz) {
        mesh.begin(0, 0, 0);
        mesh.face(side, 0, 0, 0, 1, 1, 1, 0.0, -1);
        final NativeScratch out = new NativeScratch(4 * DebugMeshData.STRIDE);
        try {
            out.ensure(4 * DebugMeshData.STRIDE);
            mesh.copySection(DebugMeshData.FILL_SECTION, out.address());
            final float[] v = new float[12];
            for (int i = 0; i < 12; i++) v[i] = memGetFloat(out.address() + (long) (i / 3) * DebugMeshData.STRIDE + (i % 3) * 4L);
            assertTrue(outward(v, 0, 1, 2, nx, ny, nz), "first triangle faces inward");
            assertTrue(outward(v, 0, 2, 3, nx, ny, nz), "second triangle faces inward");
        } finally {
            out.free();
        }
    }

    private static boolean outward(float[] v, int a, int b, int c, int nx, int ny, int nz) {
        final float ex = v[b * 3] - v[a * 3], ey = v[b * 3 + 1] - v[a * 3 + 1], ez = v[b * 3 + 2] - v[a * 3 + 2];
        final float fx = v[c * 3] - v[b * 3], fy = v[c * 3 + 1] - v[b * 3 + 1], fz = v[c * 3 + 2] - v[b * 3 + 2];
        return (ey * fz - ez * fy) * nx + (ez * fx - ex * fz) * ny + (ex * fy - ey * fx) * nz > 0.0F;
    }

    @Test
    void labelsAreAnchorRelativeAndClamped() {
        mesh.begin(ANCHOR, 64, ANCHOR);
        mesh.label(ANCHOR + 0.5, 64.25, ANCHOR - 0.5, 150, 0xFF112233);
        mesh.label(ANCHOR, 64, ANCHOR, -5, 0xFF445566);
        mesh.label(ANCHOR, 64, ANCHOR, 42, 0xFF778899);
        assertEquals(3, mesh.labels.count);
        assertEquals(0.5F, mesh.labels.pos[0], 0.0F);
        assertEquals(0.25F, mesh.labels.pos[1], 0.0F);
        assertEquals(-0.5F, mesh.labels.pos[2], 0.0F);
        assertEquals(99, mesh.labels.value[0]);
        assertEquals(0, mesh.labels.value[1]);
        assertEquals(42, mesh.labels.value[2]);
        assertEquals(0xFF112233, mesh.labels.color[0]);
        assertEquals(0xFF778899, mesh.labels.color[2]);
    }

    @Test
    void beginResetsCountsAndOnTop() {
        emit(mesh, ANCHOR + 0.25, LineWidth.THIN);
        mesh.alwaysOnTop(true);
        mesh.begin(1, 2, 3);
        assertEquals(1, mesh.anchorX());
        assertEquals(2, mesh.anchorY());
        assertEquals(3, mesh.anchorZ());
        assertEquals(0, mesh.fillVertexCount());
        assertEquals(0, mesh.fillBytes());
        assertEquals(0, mesh.labels.count);
        for (int i = 0; i < DebugMeshData.BUCKET_COUNT; i++) assertEquals(0, mesh.lineVertexCount(i));
        mesh.line(LineWidth.THIN, 0, 0, 0, 1, 1, 1, -1);
        assertEquals(2, mesh.lineVertexCount(THIN));
    }

    @Test
    void growthPreservesEarlierVertices() {
        mesh.begin(0, 0, 0);
        for (int i = 0; i < 1000; i++) mesh.line(LineWidth.THICK, i, 1, 2, i, 3, 4, i);
        final int bucket = LineWidth.THICK.ordinal();
        final long p = mesh.lineAddress(bucket);
        assertEquals(2000, mesh.lineVertexCount(bucket));
        assertEquals(32000, mesh.lineBytes(bucket));
        assertEquals(0.0F, memGetFloat(p), 0.0F);
        assertEquals(1.0F, memGetFloat(p + 4), 0.0F);
        assertEquals(3.0F, memGetFloat(p + 16 + 4), 0.0F);
        assertEquals(127.0F, memGetFloat(p + 127L * 32), 0.0F);
        assertEquals(128.0F, memGetFloat(p + 128L * 32), 0.0F);
        assertEquals(999.0F, memGetFloat(p + 999L * 32), 0.0F);
        assertEquals((byte) 231, memGetByte(p + 999L * 32 + 14));
    }
}
