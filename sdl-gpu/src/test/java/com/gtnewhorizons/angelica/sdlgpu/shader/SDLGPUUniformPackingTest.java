package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SDLGPUUniformPackingTest {

    private static FloatBuffer wrap(float... values) {
        final FloatBuffer fb = MemoryUtil.memCallocFloat(values.length);
        fb.put(values).flip();
        return fb;
    }

    private static void assertEq(float expected, float actual, String at) {
        assertEquals(expected, actual, 1e-6f, "mismatch at " + at);
    }

    private static void assertEq(float expected, float actual) {
        assertEquals(expected, actual, 1e-6f);
    }

    @Test
    void mat3_columnMajor_copiedVerbatim() {
        final FloatBuffer src = wrap(1, 2, 3, 4, 5, 6, 7, 8, 9);
        try {
            final float[] dst = new float[9];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 1, 3, false, dst, 0);
            for (int i = 0; i < 9; i++) assertEq(i + 1, dst[i], "[" + i + "]");
        } finally {
            MemoryUtil.memFree(src);
        }
    }

    @Test
    void mat3_rowMajor_transposeFlipsToColumnMajor() {
        // Input rows (1,2,3) (4,5,6) (7,8,9); output cols (1,4,7) (2,5,8) (3,6,9).
        final FloatBuffer src = wrap(1, 2, 3, 4, 5, 6, 7, 8, 9);
        try {
            final float[] dst = new float[9];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 1, 3, true, dst, 0);
            assertEq(1f, dst[0], "c0.x"); assertEq(4f, dst[1], "c0.y"); assertEq(7f, dst[2], "c0.z");
            assertEq(2f, dst[3], "c1.x"); assertEq(5f, dst[4], "c1.y"); assertEq(8f, dst[5], "c1.z");
            assertEq(3f, dst[6], "c2.x"); assertEq(6f, dst[7], "c2.y"); assertEq(9f, dst[8], "c2.z");
        } finally {
            MemoryUtil.memFree(src);
        }
    }

    @Test
    void mat3_oversizedBuffer_celeritasPattern_truncatesAtNineFloats() {
        final FloatBuffer src = wrap(1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 0, 0);
        try {
            final float[] dst = new float[9];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 1, 3, false, dst, 0);
            for (int i = 0; i < 9; i++) assertEq(i + 1, dst[i], "[" + i + "]");
        } finally {
            MemoryUtil.memFree(src);
        }
    }

    @Test
    void mat3Array_twoElements_eachCopiedColumnMajor() {
        final FloatBuffer src = wrap(
            1, 2, 3, 4, 5, 6, 7, 8, 9,
            10, 20, 30, 40, 50, 60, 70, 80, 90
        );
        try {
            final float[] dst = new float[18];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 2, 3, false, dst, 0);
            for (int i = 0; i < 9; i++) assertEq(i + 1, dst[i], "m0[" + i + "]");
            for (int i = 0; i < 9; i++) assertEq((i + 1) * 10, dst[9 + i], "m1[" + i + "]");
        } finally {
            MemoryUtil.memFree(src);
        }
    }

    @Test
    void mat2_columnMajor_copiedVerbatim() {
        final FloatBuffer src = wrap(1, 2, 3, 4);
        try {
            final float[] dst = new float[4];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 1, 2, false, dst, 0);
            assertEq(1f, dst[0], "c0.x"); assertEq(2f, dst[1], "c0.y");
            assertEq(3f, dst[2], "c1.x"); assertEq(4f, dst[3], "c1.y");
        } finally {
            MemoryUtil.memFree(src);
        }
    }

    @Test
    void mat2_rowMajor_transposeFlipsToColumnMajor() {
        final FloatBuffer src = wrap(1, 2, 3, 4);
        try {
            final float[] dst = new float[4];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 1, 2, true, dst, 0);
            assertEq(1f, dst[0]); assertEq(3f, dst[1]);
            assertEq(2f, dst[2]); assertEq(4f, dst[3]);
        } finally {
            MemoryUtil.memFree(src);
        }
    }

    @Test
    void mat4_rowMajor_transposeFlipsToColumnMajor() {
        final FloatBuffer src = wrap(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        try {
            final float[] dst = new float[16];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 1, 4, true, dst, 0);
            assertEq(1f, dst[0]);  assertEq(5f, dst[1]);  assertEq(9f, dst[2]);  assertEq(13f, dst[3]);
            assertEq(2f, dst[4]);  assertEq(6f, dst[5]);  assertEq(10f, dst[6]); assertEq(14f, dst[7]);
            assertEq(3f, dst[8]);  assertEq(7f, dst[9]);  assertEq(11f, dst[10]); assertEq(15f, dst[11]);
            assertEq(4f, dst[12]); assertEq(8f, dst[13]); assertEq(12f, dst[14]); assertEq(16f, dst[15]);
        } finally {
            MemoryUtil.memFree(src);
        }
    }

    @Test
    void mat4_columnMajor_copiedVerbatim() {
        final FloatBuffer src = wrap(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        try {
            final float[] dst = new float[16];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 1, 4, false, dst, 0);
            for (int i = 0; i < 16; i++) assertEq(i + 1, dst[i], "[" + i + "]");
        } finally {
            MemoryUtil.memFree(src);
        }
    }
}
