package com.gtnewhorizons.angelica.sdlgpu.shader;

import java.nio.FloatBuffer;

public final class MatrixMarshal {
    private MatrixMarshal() {}

    public static void marshalMatrixToColumnMajor(FloatBuffer src, int srcPos, int count, int size, boolean transpose, float[] dst, int dstPos) {
        final int floatsPerMatrix = size * size;
        if (transpose) {
            for (int e = 0; e < count; e++) {
                final int sBase = srcPos + e * floatsPerMatrix;
                final int dBase = dstPos + e * floatsPerMatrix;
                for (int col = 0; col < size; col++) {
                    for (int row = 0; row < size; row++) {
                        dst[dBase + col * size + row] = src.get(sBase + row * size + col);
                    }
                }
            }
        } else {
            for (int e = 0; e < count; e++) {
                src.get(srcPos + e * floatsPerMatrix, dst, dstPos + e * floatsPerMatrix, floatsPerMatrix);
            }
        }
    }
}
