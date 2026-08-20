package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public final class Std140Writer {

    private static final int MAT_COLUMN_STRIDE_BYTES = 16;
    private static final int MAT_COLUMN_STRIDE_FLOATS = MAT_COLUMN_STRIDE_BYTES / 4;

    private Std140Writer() {}

    public static void write(FloatBuffer ubo, ShaderManager.UniformMemberInfo info, float[] src, int srcCount) {
        final int rows = info.vectorSize();
        final int cols = info.columns();
        if (rows <= 0 || cols <= 0) {
            throw new IllegalStateException("ShaderManager.UniformMemberInfo missing type descriptor (vectorSize=" + rows + ", columns=" + cols + "). SPVC reflection failed to capture the member's type - cannot dispatch std140 layout.");
        }
        final int floatsPerElement = rows * cols;
        if (floatsPerElement == 0) return;

        final int arrayLen = info.arrayLen();
        final int maxFromSrc = src.length / floatsPerElement;
        final int count = Math.min(Math.min(srcCount, arrayLen), maxFromSrc);
        if (count <= 0) return;

        final int byteOffset = info.offset();
        final int elementStrideBytes = info.arrayStride() > 0 ? info.arrayStride() : floatsPerElement * 4;

        final boolean elementContig = (cols == 1) || rows == 4;

        if (elementContig) {
            if (elementStrideBytes == floatsPerElement * 4) {
                bulkPut(ubo, byteOffset >> 2, src, 0, floatsPerElement * count);
            } else {
                for (int e = 0; e < count; e++) {
                    bulkPut(ubo, (byteOffset + e * elementStrideBytes) >> 2,
                        src, e * floatsPerElement, floatsPerElement);
                }
            }
            return;
        }

        for (int e = 0; e < count; e++) {
            final int elementByteOffset = byteOffset + e * elementStrideBytes;
            final int srcOffset = e * floatsPerElement;
            for (int c = 0; c < cols; c++) {
                writeColumn(ubo, elementByteOffset + c * MAT_COLUMN_STRIDE_BYTES, src, srcOffset + c * rows, rows);
            }
        }
    }

    private static void bulkPut(FloatBuffer ubo, int dstFloatIdx, float[] src, int srcOff, int floats) {
        if (floats <= 0) return;
        final int writableFloats = ubo.capacity() - dstFloatIdx;
        if (writableFloats <= 0) return;
        final int writeN = Math.min(floats, writableFloats);
        final long dstAddr = MemoryUtil.memAddress(ubo) + ((long) dstFloatIdx) * 4L;
        MemoryAccess.copyFloatsToAddr(src, srcOff, dstAddr, writeN);
    }

    private static void writeColumn(FloatBuffer ubo, int byteOffset, float[] src, int srcOffset, int n) {
        final int floatOffset = byteOffset >> 2;
        final int writableFloats = Math.max(0, ubo.capacity() - floatOffset);
        final int writeN = Math.min(n, writableFloats);
        if (writeN > 0) {
            final long dstAddr = MemoryUtil.memAddress(ubo) + ((long) floatOffset) * 4L;
            MemoryAccess.copyFloatsToAddr(src, srcOffset, dstAddr, writeN);
        }
        final int padN = Math.min(MAT_COLUMN_STRIDE_FLOATS, writableFloats) - writeN;
        if (padN > 0) {
            final long padAddr = MemoryUtil.memAddress(ubo) + ((long) (floatOffset + writeN)) * 4L;
            for (int i = 0; i < padN; i++) {
                MemoryUtil.memPutFloat(padAddr + ((long) i) * 4L, 0f);
            }
        }
    }
}
