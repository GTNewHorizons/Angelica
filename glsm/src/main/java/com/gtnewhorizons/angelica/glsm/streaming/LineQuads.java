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

    static {
        for (int flags = 0; flags < QUAD_FORMATS.length; flags++) {
            if ((flags & VertexFlags.NORMAL_BIT) == 0) continue;
            final VertexFormatElement[] elements = DefaultVertexFormat.ALL_FORMATS[flags].elementsArray.clone();
            for (int i = 0; i < elements.length; i++) {
                if (elements[i] == DefaultVertexFormat.NORMAL_ELEMENT) elements[i] = NORMAL_SLOT;
            }
            QUAD_FORMATS[flags] = new VertexFormat(elements);
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
        final VertexFormat dstFormat = quadFormat(srcFormat);
        final VertexFormatElement[] dstElements = dstFormat.elementsArray;
        final int[] srcOffsets = new int[dstElements.length];
        for (int i = 0; i < dstElements.length; i++) {
            srcOffsets[i] = offsetOf(srcFormat, dstElements[i]);
        }

        final int srcStride = srcFormat.getVertexSize();
        final int segments = segmentCount(drawMode, vertexCount);
        final float[] forA = new float[3];
        final float[] forB = new float[3];
        long out = dst;
        for (int s = 0; s < segments; s++) {
            final int a = drawMode == GL11.GL_LINES ? s * 2 : s;
            final int b = drawMode == GL11.GL_LINE_LOOP && s == vertexCount - 1 ? 0 : a + 1;
            final long srcA = src + (long) a * srcStride;
            final long srcB = src + (long) b * srcStride;
            if (otherEnd) {
                readPosition(srcB, forA);
                readPosition(srcA, forB);
            } else {
                unitDirection(srcA, srcB, forA);
                System.arraycopy(forA, 0, forB, 0, 3);
            }

            out = writeVertex(srcA, out, dstElements, srcOffsets, forA);
            out = writeVertex(srcA, out, dstElements, srcOffsets, forA);
            out = writeVertex(srcB, out, dstElements, srcOffsets, forB);
            out = writeVertex(srcA, out, dstElements, srcOffsets, forA);
            out = writeVertex(srcB, out, dstElements, srcOffsets, forB);
            out = writeVertex(srcB, out, dstElements, srcOffsets, forB);
        }
    }

    private static void readPosition(long vertex, float[] out) {
        out[0] = memGetFloat(vertex);
        out[1] = memGetFloat(vertex + 4);
        out[2] = memGetFloat(vertex + 8);
    }

    private static long writeVertex(long src, long dst, VertexFormatElement[] dstElements, int[] srcOffsets, float[] normalSlot) {
        long out = dst;
        for (int i = 0; i < dstElements.length; i++) {
            final VertexFormatElement element = dstElements[i];
            final int size = element.getByteSize();
            if (element == NORMAL_SLOT) {
                memPutFloat(out, normalSlot[0]);
                memPutFloat(out + 4, normalSlot[1]);
                memPutFloat(out + 8, normalSlot[2]);
            } else {
                memCopy(src + srcOffsets[i], out, size);
            }
            out += size;
        }
        return out;
    }

    private static void unitDirection(long a, long b, float[] out) {
        final float dx = memGetFloat(b) - memGetFloat(a);
        final float dy = memGetFloat(b + 4) - memGetFloat(a + 4);
        final float dz = memGetFloat(b + 8) - memGetFloat(a + 8);
        final float lengthSquared = dx * dx + dy * dy + dz * dz;
        final float scale = lengthSquared == 0.0f ? 0.0f : 1.0f / (float) Math.sqrt(lengthSquared);
        out[0] = dx * scale;
        out[1] = dy * scale;
        out[2] = dz * scale;
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
