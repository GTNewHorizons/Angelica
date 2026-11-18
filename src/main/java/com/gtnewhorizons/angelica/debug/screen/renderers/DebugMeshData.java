package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.debug.render.DebugGeometry;
import com.gtnewhorizons.angelica.debug.render.NativeScratch;

import java.util.Arrays;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;

final class DebugMeshData {

    enum LineWidth {

        THIN(1),
        MEDIUM(2),
        THICK(4);

        final float pixels;

        LineWidth(float pixels) {
            this.pixels = pixels;
        }
    }

    static final VertexFormat PLAIN = DefaultVertexFormat.ALL_FORMATS[VertexFlags.COLOR_BIT];
    static final int STRIDE = PLAIN.getVertexSize();
    static final int WIDTH_COUNT = LineWidth.values().length;
    static final int BUCKET_COUNT = 2 * WIDTH_COUNT;
    static final int FILL_SECTION = 0;

    private static final int INITIAL_BYTES = 4096;
    private static final int SECTION_COUNT = 1 + BUCKET_COUNT;
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;
    private static final int TAG_QUAD = 0x51554144;
    private static final int TAG_LINE = 0x4C494E45;
    private static final int TAG_LABEL = 0x4C41424C;

    private final NativeScratch[] sections = new NativeScratch[SECTION_COUNT];
    private final int[] used = new int[SECTION_COUNT];
    final DebugLabels labels = new DebugLabels();
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private boolean onTop;
    private long hash = FNV_OFFSET;

    DebugMeshData() {
        for (int i = 0; i < SECTION_COUNT; i++) sections[i] = new NativeScratch(INITIAL_BYTES);
    }

    static int bucket(LineWidth width, boolean onTop) {
        return width.ordinal() + (onTop ? WIDTH_COUNT : 0);
    }

    static int lineSection(int bucket) {
        return 1 + bucket;
    }

    void begin(int anchorX, int anchorY, int anchorZ) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        Arrays.fill(used, 0);
        labels.count = 0;
        onTop = false;
        hash = FNV_OFFSET;
        mix(anchorX);
        mix(anchorY);
        mix(anchorZ);
    }

    void free() {
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i].free();
            used[i] = 0;
        }
        labels.free();
    }

    void line(LineWidth width, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        final int bucket = bucket(width, onTop);
        final long p = reserve(lineSection(bucket), 2 * STRIDE);
        mix(TAG_LINE);
        mix(bucket);
        vertex(p, x0, y0, z0, argb);
        vertex(p + STRIDE, x1, y1, z1, argb);
    }

    void quad(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        final long p = reserve(FILL_SECTION, 4 * STRIDE);
        mix(TAG_QUAD);
        vertex(p, x0, y0, z0, argb);
        vertex(p + STRIDE, x1, y1, z1, argb);
        vertex(p + 2 * STRIDE, x2, y2, z2, argb);
        vertex(p + 3 * STRIDE, x3, y3, z3, argb);
    }

    void label(double x, double y, double z, int value, int argb) {
        final float fx = (float) (x - anchorX);
        final float fy = (float) (y - anchorY);
        final float fz = (float) (z - anchorZ);
        final int clamped = value < 0 ? 0 : Math.min(value, DebugLabelBatch.MAX_VALUE);
        labels.add(fx, fy, fz, clamped, argb);
        mix(TAG_LABEL);
        mix(Float.floatToRawIntBits(fx));
        mix(Float.floatToRawIntBits(fy));
        mix(Float.floatToRawIntBits(fz));
        mix(clamped);
        mix(argb);
    }

    void alwaysOnTop(boolean onTop) {
        this.onTop = onTop;
    }

    void lineBox(LineWidth width, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        line(width, x0, y0, z0, x1, y0, z0, argb);
        line(width, x1, y0, z0, x1, y0, z1, argb);
        line(width, x1, y0, z1, x0, y0, z1, argb);
        line(width, x0, y0, z1, x0, y0, z0, argb);
        line(width, x0, y1, z0, x1, y1, z0, argb);
        line(width, x1, y1, z0, x1, y1, z1, argb);
        line(width, x1, y1, z1, x0, y1, z1, argb);
        line(width, x0, y1, z1, x0, y1, z0, argb);
        line(width, x0, y0, z0, x0, y1, z0, argb);
        line(width, x1, y0, z0, x1, y1, z0, argb);
        line(width, x1, y0, z1, x1, y1, z1, argb);
        line(width, x0, y0, z1, x0, y1, z1, argb);
    }

    void ring(LineWidth width, double x0, double y, double z0, double x1, double z1, int argb) {
        line(width, x0, y, z0, x0, y, z1, argb);
        line(width, x0, y, z1, x1, y, z1, argb);
        line(width, x1, y, z1, x1, y, z0, argb);
        line(width, x1, y, z0, x0, y, z0, argb);
    }

    void fillBox(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        face(0, x0, y0, z0, x1, y1, z1, 0.0, argb);
        face(1, x0, y0, z0, x1, y1, z1, 0.0, argb);
        face(4, x0, y0, z0, x1, y1, z1, 0.0, argb);
        face(5, x0, y0, z0, x1, y1, z1, 0.0, argb);
        face(2, x0, y0, z0, x1, y1, z1, 0.0, argb);
        face(3, x0, y0, z0, x1, y1, z1, 0.0, argb);
    }

    void face(int side, double x0, double y0, double z0, double x1, double y1, double z1, double offset, int argb) {
        switch (side) {
            case 0 -> quad(x1, y0 - offset, z0, x1, y0 - offset, z1, x0, y0 - offset, z1, x0, y0 - offset, z0, argb);
            case 1 -> quad(x0, y1 + offset, z0, x0, y1 + offset, z1, x1, y1 + offset, z1, x1, y1 + offset, z0, argb);
            case 2 -> quad(x0, y0, z0 - offset, x0, y1, z0 - offset, x1, y1, z0 - offset, x1, y0, z0 - offset, argb);
            case 3 -> quad(x1, y0, z1 + offset, x1, y1, z1 + offset, x0, y1, z1 + offset, x0, y0, z1 + offset, argb);
            case 4 -> quad(x0 - offset, y0, z0, x0 - offset, y0, z1, x0 - offset, y1, z1, x0 - offset, y1, z0, argb);
            default -> quad(x1 + offset, y1, z0, x1 + offset, y1, z1, x1 + offset, y0, z1, x1 + offset, y0, z0, argb);
        }
    }

    int anchorX() {
        return anchorX;
    }

    int anchorY() {
        return anchorY;
    }

    int anchorZ() {
        return anchorZ;
    }

    void copySection(int section, long destination) {
        memCopy(sections[section].address(), destination, used[section]);
    }

    int fillBytes() {
        return used[FILL_SECTION];
    }

    int fillVertexCount() {
        return used[FILL_SECTION] / STRIDE;
    }

    long lineAddress(int bucket) {
        return sections[lineSection(bucket)].address();
    }

    int lineBytes(int bucket) {
        return used[lineSection(bucket)];
    }

    int lineVertexCount(int bucket) {
        return used[lineSection(bucket)] / STRIDE;
    }

    long hash() {
        return hash;
    }

    private long reserve(int section, int bytes) {
        final int start = used[section];
        final int end = start + bytes;
        final NativeScratch scratch = sections[section];
        scratch.ensure(end);
        used[section] = end;
        return scratch.address() + start;
    }

    private void vertex(long p, double x, double y, double z, int argb) {
        final float fx = (float) (x - anchorX);
        final float fy = (float) (y - anchorY);
        final float fz = (float) (z - anchorZ);
        DebugGeometry.putPosition(p, fx, fy, fz);
        DebugGeometry.putColor(p + 12, argb);
        mix(Float.floatToRawIntBits(fx));
        mix(Float.floatToRawIntBits(fy));
        mix(Float.floatToRawIntBits(fz));
        mix(argb);
    }

    private void mix(int value) {
        hash = (hash ^ (value & 0xFFFFFFFFL)) * FNV_PRIME;
    }
}
