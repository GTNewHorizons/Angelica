package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.stream.Stream;

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

    private static Arguments matrix(String name, int dim, boolean transpose, float... expected) {
        return Arguments.of(name, dim, transpose, expected);
    }

    /** Source is always 1..dim*dim. */
    static Stream<Arguments> matrices() {
        return Stream.of(
            matrix("mat2 column-major: verbatim", 2, false, 1, 2, 3, 4),
            matrix("mat2 row-major: transposed", 2, true, 1, 3, 2, 4),
            matrix("mat3 column-major: verbatim", 3, false, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            matrix("mat3 row-major: transposed", 3, true, 1, 4, 7, 2, 5, 8, 3, 6, 9),
            matrix("mat4 column-major: verbatim", 4, false, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
            matrix("mat4 row-major: transposed", 4, true, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15, 4, 8, 12, 16));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrices")
    void singleMatrixMarshalsToColumnMajor(String name, int dim, boolean transpose, float[] expected) {
        final float[] values = new float[dim * dim];
        for (int i = 0; i < values.length; i++) values[i] = i + 1;
        final FloatBuffer src = wrap(values);
        try {
            final float[] dst = new float[dim * dim];
            MatrixMarshal.marshalMatrixToColumnMajor(src, 0, 1, dim, transpose, dst, 0);
            for (int i = 0; i < dst.length; i++) assertEq(expected[i], dst[i], "[" + i + "]");
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
}
