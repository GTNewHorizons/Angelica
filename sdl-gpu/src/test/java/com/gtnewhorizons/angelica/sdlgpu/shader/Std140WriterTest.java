package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.spvc.Spvc;

import java.nio.FloatBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Std140WriterTest {
    private static final int FP32 = Spvc.SPVC_BASETYPE_FP32;

    private static FloatBuffer outBuf(int floats) {
        return MemoryUtil.memCallocFloat(floats);
    }

    private static ShaderManager.UniformMemberInfo single(int byteOffset, int byteSize, int vectorSize, int columns) {
        return new ShaderManager.UniformMemberInfo(byteOffset, byteSize, /*arrayStride*/ 0, /*isVertex*/ true, vectorSize, columns, FP32, /*arrayLen*/ 1);
    }

    private static ShaderManager.UniformMemberInfo array(int byteOffset, int arrayStride, int arrayLen, int vectorSize, int columns) {
        return new ShaderManager.UniformMemberInfo(byteOffset, arrayStride * arrayLen, arrayStride, /*isVertex*/ true, vectorSize, columns, FP32, arrayLen);
    }

    private static void assertEq(float expected, float actual, String at) {
        assertEquals(expected, actual, 1e-6f, "mismatch at " + at);
    }

    private static void assertEq(float expected, float actual) {
        assertEquals(expected, actual, 1e-6f);
    }

    private static Arguments nonArray(String name, int byteOffset, int vectorSize, float[] values, float... expectedUbo) {
        return Arguments.of(name, byteOffset, vectorSize, values, expectedUbo);
    }

    /** Expected is the whole 8-float UBO, so slots the write must not touch are checked too. */
    static Stream<Arguments> nonArrays() {
        return Stream.of(
            nonArray("scalar at byte offset 4", 4, 1, new float[]{42f}, 0, 42, 0, 0, 0, 0, 0, 0),
            nonArray("vec2", 0, 2, new float[]{1f, 2f}, 1, 2, 0, 0, 0, 0, 0, 0),
            nonArray("vec3 leaves the fourth lane alone", 0, 3, new float[]{1f, 2f, 3f}, 1, 2, 3, 0, 0, 0, 0, 0),
            nonArray("vec4", 0, 4, new float[]{1f, 2f, 3f, 4f}, 1, 2, 3, 4, 0, 0, 0, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonArrays")
    void nonArray_writesPackedComponentsAtOffset(String name, int byteOffset, int vectorSize, float[] values, float[] expectedUbo) {
        final FloatBuffer ubo = outBuf(expectedUbo.length);
        try {
            Std140Writer.write(ubo, single(byteOffset, vectorSize * 4, vectorSize, 1), values, 1);
            for (int i = 0; i < expectedUbo.length; i++) assertEq(expectedUbo[i], ubo.get(i), "[" + i + "]");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    private static float[] ramp(int n) {
        final float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = i + 1;
        return out;
    }

    static Stream<Arguments> layouts() {
        final float[] mat3Src = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        final float[] mat3Ubo = {1, 2, 3, 0, 4, 5, 6, 0, 7, 8, 9, 0};
        return Stream.of(
            Arguments.of("scalar array at stride 16", array(0, 16, 4, 1, 1), new float[]{1, 2, 3, 4}, 4,
                new float[]{1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4, 0, 0, 0}),
            Arguments.of("vec3 array zero-pads the fourth lane", array(0, 16, 4, 3, 1),
                new float[]{1, 2, 3, 10, 20, 30, 100, 200, 300, -1, -2, -3}, 4,
                new float[]{1, 2, 3, 0, 10, 20, 30, 0, 100, 200, 300, 0, -1, -2, -3, 0}),
            Arguments.of("vec4 array needs no padding", array(0, 16, 2, 4, 1), ramp(8), 2, ramp(8)),
            Arguments.of("mat2 columns are vec4 each", single(0, 32, 2, 2), new float[]{1, 2, 3, 4}, 1,
                new float[]{1, 2, 0, 0, 3, 4, 0, 0}),
            Arguments.of("mat3 columns padded to vec4", single(0, 48, 3, 3), mat3Src, 1, mat3Ubo),
            Arguments.of("mat3 padded when reflection under-reports size", single(0, 36, 3, 3), mat3Src, 1, mat3Ubo),
            Arguments.of("mat4 needs no padding", single(0, 64, 4, 4), ramp(16), 1, ramp(16)),
            Arguments.of("mat3 array pads each matrix", array(0, 48, 2, 3, 3),
                new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 40, 50, 60, 70, 80, 90}, 2,
                new float[]{1, 2, 3, 0, 4, 5, 6, 0, 7, 8, 9, 0, 10, 20, 30, 0, 40, 50, 60, 0, 70, 80, 90, 0}),
            Arguments.of("mat4 array at stride 64", array(0, 64, 2, 4, 4), ramp(32), 2, ramp(32)),
            Arguments.of("srcCount clamped to array length", array(0, 16, 2, 1, 1), new float[]{1, 2, 3, 4}, 4,
                new float[]{1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}),
            Arguments.of("srcCount clamped to source size", array(0, 16, 4, 1, 1), new float[]{7, 8}, 4,
                new float[]{7, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layouts")
    void writesLayout(String name, ShaderManager.UniformMemberInfo info, float[] src, int srcCount, float[] expectedUbo) {
        final FloatBuffer ubo = outBuf(expectedUbo.length);
        try {
            Std140Writer.write(ubo, info, src, srcCount);
            for (int i = 0; i < expectedUbo.length; i++) assertEq(expectedUbo[i], ubo.get(i), "[" + i + "]");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void nonZeroOffset_doesNotDisturbEarlierLanes() {
        final FloatBuffer ubo = outBuf(16);
        try {
            for (int i = 0; i < 16; i++) ubo.put(i, -7f);
            Std140Writer.write(ubo, single(/*off*/ 32, /*size*/ 12, 3, 1), new float[]{1, 2, 3}, 1);
            for (int i = 0; i < 8; i++) assertEq(-7f, ubo.get(i), "preserved [" + i + "]");
            assertEq(1f, ubo.get(8));
            assertEq(2f, ubo.get(9));
            assertEq(3f, ubo.get(10));
            assertEq(-7f, ubo.get(11), "preserved (vec3 leaves 4th lane untouched)");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void degenerateTypeDescriptor_throws() {
        assertThrows(IllegalStateException.class, () -> {
            final FloatBuffer ubo = outBuf(4);
            try {
                Std140Writer.write(ubo,
                    new ShaderManager.UniformMemberInfo(0, 4, 0, true, /*vec*/ 0, /*cols*/ 0, FP32, 1),
                    new float[]{1f}, 1);
            } finally {
                MemoryUtil.memFree(ubo);
            }
        });
    }

    @Test
    void regression_irisNormalMatrix_columnMajorJoml_paddedCorrectlyAtRealOffset() {
        final FloatBuffer ubo = outBuf(1344 / 4 + 12);
        try {
            Std140Writer.write(ubo, single(/*off*/ 1344, /*size*/ 36, 3, 3),
                new float[]{
                    0.8875f, 0f, 0.4607f,        // col 0
                    0.2285f, 0.8684f, -0.4401f,  // col 1
                    -0.4001f, 0.4959f, 0.7707f   // col 2
                }, 1);
            final int b = 1344 / 4;
            assertEq(0.8875f, ubo.get(b + 0), "c0.x");
            assertEq(0f,      ubo.get(b + 1), "c0.y");
            assertEq(0.4607f, ubo.get(b + 2), "c0.z");
            assertEq(0f,      ubo.get(b + 3), "c0.pad");
            assertEq(0.2285f, ubo.get(b + 4), "c1.x");
            assertEq(0.8684f, ubo.get(b + 5), "c1.y");
            assertEq(-0.4401f, ubo.get(b + 6), "c1.z");
            assertEq(0f,      ubo.get(b + 7), "c1.pad");
            assertEq(-0.4001f, ubo.get(b + 8), "c2.x");
            assertEq(0.4959f, ubo.get(b + 9), "c2.y");
            assertEq(0.7707f, ubo.get(b + 10), "c2.z");
            assertEq(0f,      ubo.get(b + 11), "c2.pad");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }
}
