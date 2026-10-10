package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;

/**
 * Rewrites line primitives as quads for shaders that widen lines in the vertex stage.
 */
public final class LineQuads {

    public static final int VERTICES_PER_SEGMENT = 6;

    private static final VertexFormatElement NORMAL_SLOT = new VertexFormatElement(0, VertexFormatElement.Type.FLOAT,
        VertexFormatElement.Usage.NORMAL, 3, VertexFlags.NORMAL_BIT, DefaultVertexFormat.NORMAL_ELEMENT.getWriter());

    private static final VertexFormat[] QUAD_FORMATS = new VertexFormat[VertexFlags.BITSET_SIZE];
    private static final int[][] SOURCE_OFFSETS = new int[VertexFlags.BITSET_SIZE][];

    static {
        for (int flags = 0; flags < QUAD_FORMATS.length; flags++) {
            if ((flags & VertexFlags.NORMAL_BIT) == 0) continue;
            final VertexFormatElement[] elements = DefaultVertexFormat.ALL_FORMATS[flags].elementsArray.clone();
            for (int i = 0; i < elements.length; i++) {
                if (elements[i] == DefaultVertexFormat.NORMAL_ELEMENT) elements[i] = NORMAL_SLOT;
            }
            QUAD_FORMATS[flags] = new VertexFormat(elements);
        }
        for (int flags = 0; flags < SOURCE_OFFSETS.length; flags++) {
            final VertexFormatElement[] quadElements = QUAD_FORMATS[flags | VertexFlags.NORMAL_BIT].elementsArray;
            final int[] offsets = new int[quadElements.length];
            for (int i = 0; i < quadElements.length; i++) {
                offsets[i] = offsetOf(DefaultVertexFormat.ALL_FORMATS[flags], quadElements[i]);
            }
            SOURCE_OFFSETS[flags] = offsets;
        }
    }

    private LineQuads() {}

    public static boolean isLineMode(int drawMode) {
        return drawMode == GL11.GL_LINES || drawMode == GL11.GL_LINE_STRIP || drawMode == GL11.GL_LINE_LOOP;
    }

    public static int segmentCount(int drawMode, int vertexCount) {
        return switch (drawMode) {
            case GL11.GL_LINES -> vertexCount / 2;
            case GL11.GL_LINE_STRIP -> Math.max(0, vertexCount - 1);
            case GL11.GL_LINE_LOOP -> vertexCount < 2 ? 0 : vertexCount;
            default -> 0;
        };
    }

    public static boolean disableCulling() {
        final boolean wasEnabled = GLStateManager.getCullState().isEnabled();
        if (wasEnabled) {
            GLStateManager.disableCull();
        }
        return wasEnabled;
    }

    public static void restoreCulling(boolean wasEnabled) {
        if (wasEnabled) {
            GLStateManager.enableCull();
        }
    }

    public static VertexFormat quadFormat(VertexFormat lineFormat) {
        return QUAD_FORMATS[lineFormat.getVertexFlags() | VertexFlags.NORMAL_BIT];
    }

    public static boolean isQuadFormat(VertexFormat format) {
        return QUAD_FORMATS[format.getVertexFlags()] == format;
    }

    public static void expandWithDirection(long src, VertexFormat srcFormat, int drawMode, int vertexCount, long dst) {
        expand(src, srcFormat, drawMode, vertexCount, dst, false);
    }

    public static void expandWithOtherEnd(long src, VertexFormat srcFormat, int drawMode, int vertexCount, long dst) {
        expand(src, srcFormat, drawMode, vertexCount, dst, true);
    }

    private static void expand(long src, VertexFormat srcFormat, int drawMode, int vertexCount, long dst, boolean otherEnd) {
        final VertexFormatElement[] dstElements = quadFormat(srcFormat).elementsArray;
        final int[] srcOffsets = SOURCE_OFFSETS[srcFormat.getVertexFlags()];
        final int srcStride = srcFormat.getVertexSize();
        final int segments = segmentCount(drawMode, vertexCount);
        long out = dst;
        for (int s = 0; s < segments; s++) {
            final int a = drawMode == GL11.GL_LINES ? s * 2 : s;
            final int b = drawMode == GL11.GL_LINE_LOOP && s == vertexCount - 1 ? 0 : a + 1;
            final long srcA = src + (long) a * srcStride;
            final long srcB = src + (long) b * srcStride;
            // What A's and B's corners carry in the normal slot
            final float ax, ay, az, bx, by, bz;
            if (otherEnd) {
                ax = memGetFloat(srcB); ay = memGetFloat(srcB + 4); az = memGetFloat(srcB + 8);
                bx = memGetFloat(srcA); by = memGetFloat(srcA + 4); bz = memGetFloat(srcA + 8);
            } else {
                final float x = memGetFloat(srcB) - memGetFloat(srcA);
                final float y = memGetFloat(srcB + 4) - memGetFloat(srcA + 4);
                final float z = memGetFloat(srcB + 8) - memGetFloat(srcA + 8);
                final float lengthSquared = x * x + y * y + z * z;
                final float scale = lengthSquared == 0.0f ? 0.0f : 1.0f / (float) Math.sqrt(lengthSquared);
                ax = bx = x * scale;
                ay = by = y * scale;
                az = bz = z * scale;
            }

            out = writeVertex(srcA, out, dstElements, srcOffsets, ax, ay, az);
            out = writeVertex(srcA, out, dstElements, srcOffsets, ax, ay, az);
            out = writeVertex(srcB, out, dstElements, srcOffsets, bx, by, bz);
            out = writeVertex(srcA, out, dstElements, srcOffsets, ax, ay, az);
            out = writeVertex(srcB, out, dstElements, srcOffsets, bx, by, bz);
            out = writeVertex(srcB, out, dstElements, srcOffsets, bx, by, bz);
        }
    }

    private static long writeVertex(long src, long dst, VertexFormatElement[] dstElements, int[] srcOffsets, float x, float y, float z) {
        long out = dst;
        for (int i = 0; i < dstElements.length; i++) {
            final VertexFormatElement element = dstElements[i];
            final int size = element.getByteSize();
            if (element == NORMAL_SLOT) {
                memPutFloat(out, x);
                memPutFloat(out + 4, y);
                memPutFloat(out + 8, z);
            } else {
                memCopy(src + srcOffsets[i], out, size);
            }
            out += size;
        }
        return out;
    }

    private static int offsetOf(VertexFormat format, VertexFormatElement element) {
        int offset = 0;
        for (VertexFormatElement candidate : format.elementsArray) {
            if (candidate == element) {
                return offset;
            }
            offset += candidate.getByteSize();
        }
        return -1;
    }
}
