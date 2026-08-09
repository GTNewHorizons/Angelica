package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.spvc.Spvc;

import java.nio.FloatBuffer;

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

    @Test
    void scalar_writesOneFloatAtOffset() {
        final FloatBuffer ubo = outBuf(8);
        try {
            Std140Writer.write(ubo, single(/*off*/ 4, /*size*/ 4, 1, 1), new float[]{42f}, 1);
            assertEq(0f, ubo.get(0), "[0] before offset");
            assertEq(42f, ubo.get(1), "[1] = scalar");
            assertEq(0f, ubo.get(2), "[2] untouched");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void scalarArray_writesEachAtStride16() {
        final FloatBuffer ubo = outBuf(16);
        try {
            Std140Writer.write(ubo, array(/*off*/ 0, /*stride*/ 16, /*len*/ 4, 1, 1), new float[]{1, 2, 3, 4}, 4);
            assertEq(1f, ubo.get(0), "elem0");
            assertEq(0f, ubo.get(1), "elem0 pad");
            assertEq(2f, ubo.get(4), "elem1");
            assertEq(3f, ubo.get(8), "elem2");
            assertEq(4f, ubo.get(12), "elem3");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void vec2_writesTwoFloatsPacked() {
        final FloatBuffer ubo = outBuf(8);
        try {
            Std140Writer.write(ubo, single(/*off*/ 0, /*size*/ 8, 2, 1), new float[]{1f, 2f}, 1);
            assertEq(1f, ubo.get(0), "x");
            assertEq(2f, ubo.get(1), "y");
            assertEq(0f, ubo.get(2), "after vec2");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void vec3_writesThreeFloatsPacked_noTrailingPaddingTouched() {
        final FloatBuffer ubo = outBuf(8);
        try {
            Std140Writer.write(ubo, single(/*off*/ 0, /*size*/ 12, 3, 1), new float[]{1f, 2f, 3f}, 1);
            assertEq(1f, ubo.get(0), "x");
            assertEq(2f, ubo.get(1), "y");
            assertEq(3f, ubo.get(2), "z");
            assertEq(0f, ubo.get(3), "no spurious write past vec3");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void vec4_writesFourFloatsPacked() {
        final FloatBuffer ubo = outBuf(8);
        try {
            Std140Writer.write(ubo, single(/*off*/ 0, /*size*/ 16, 4, 1), new float[]{1f, 2f, 3f, 4f}, 1);
            assertEq(1f, ubo.get(0), "x");
            assertEq(2f, ubo.get(1), "y");
            assertEq(3f, ubo.get(2), "z");
            assertEq(4f, ubo.get(3), "w");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void vec3Array_eachElementAtStride16_zeroPaddedFourthLane() {

        final FloatBuffer ubo = outBuf(16);
        try {
            Std140Writer.write(ubo, array(/*off*/ 0, /*stride*/ 16, /*len*/ 4, 3, 1),
                new float[]{1, 2, 3,  10, 20, 30,  100, 200, 300,  -1, -2, -3}, 4);
            assertEq(1f, ubo.get(0), "e0.x"); assertEq(2f, ubo.get(1), "e0.y"); assertEq(3f, ubo.get(2), "e0.z");
            assertEq(0f, ubo.get(3), "e0.pad");
            assertEq(10f, ubo.get(4), "e1.x"); assertEq(20f, ubo.get(5), "e1.y"); assertEq(30f, ubo.get(6), "e1.z");
            assertEq(0f, ubo.get(7), "e1.pad");
            assertEq(100f, ubo.get(8), "e2.x"); assertEq(200f, ubo.get(9), "e2.y"); assertEq(300f, ubo.get(10), "e2.z");
            assertEq(0f, ubo.get(11), "e2.pad");
            assertEq(-1f, ubo.get(12), "e3.x"); assertEq(-2f, ubo.get(13), "e3.y"); assertEq(-3f, ubo.get(14), "e3.z");
            assertEq(0f, ubo.get(15), "e3.pad");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void vec4Array_eachElementAtStride16_noPaddingNeeded() {
        final FloatBuffer ubo = outBuf(8);
        try {
            Std140Writer.write(ubo, array(/*off*/ 0, /*stride*/ 16, /*len*/ 2, 4, 1), new float[]{1, 2, 3, 4,  5, 6, 7, 8}, 2);
            assertEq(1f, ubo.get(0)); assertEq(2f, ubo.get(1)); assertEq(3f, ubo.get(2)); assertEq(4f, ubo.get(3));
            assertEq(5f, ubo.get(4)); assertEq(6f, ubo.get(5)); assertEq(7f, ubo.get(6)); assertEq(8f, ubo.get(7));
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void mat2_threeColsTimesVec4Each() {
        final FloatBuffer ubo = outBuf(8);
        try {
            Std140Writer.write(ubo, single(/*off*/ 0, /*size*/ 32, /*rows*/ 2, /*cols*/ 2), new float[]{1, 2, 3, 4}, 1);
            assertEq(1f, ubo.get(0), "c0.x"); assertEq(2f, ubo.get(1), "c0.y");
            assertEq(0f, ubo.get(2), "c0.pad2"); assertEq(0f, ubo.get(3), "c0.pad3");
            assertEq(3f, ubo.get(4), "c1.x"); assertEq(4f, ubo.get(5), "c1.y");
            assertEq(0f, ubo.get(6), "c1.pad2"); assertEq(0f, ubo.get(7), "c1.pad3");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void mat3_threeColsEachPaddedToVec4() {
        final FloatBuffer ubo = outBuf(12);
        try {
            Std140Writer.write(ubo, single(/*off*/ 0, /*size*/ 48, 3, 3), new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, 1);
            assertEq(1f, ubo.get(0)); assertEq(2f, ubo.get(1)); assertEq(3f, ubo.get(2)); assertEq(0f, ubo.get(3));
            assertEq(4f, ubo.get(4)); assertEq(5f, ubo.get(5)); assertEq(6f, ubo.get(6)); assertEq(0f, ubo.get(7));
            assertEq(7f, ubo.get(8)); assertEq(8f, ubo.get(9)); assertEq(9f, ubo.get(10)); assertEq(0f, ubo.get(11));
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void mat3_reflectionUnderreportsSize_paddingStillApplied() {
        final FloatBuffer ubo = outBuf(12);
        try {
            Std140Writer.write(ubo, single(/*off*/ 0, /*size*/ 36, 3, 3), new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, 1);
            assertEq(1f, ubo.get(0)); assertEq(2f, ubo.get(1)); assertEq(3f, ubo.get(2)); assertEq(0f, ubo.get(3));
            assertEq(4f, ubo.get(4)); assertEq(5f, ubo.get(5)); assertEq(6f, ubo.get(6)); assertEq(0f, ubo.get(7));
            assertEq(7f, ubo.get(8)); assertEq(8f, ubo.get(9)); assertEq(9f, ubo.get(10)); assertEq(0f, ubo.get(11));
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void mat4_fourColsNoPaddingNeeded() {
        final FloatBuffer ubo = outBuf(16);
        try {
            Std140Writer.write(ubo, single(/*off*/ 0, /*size*/ 64, 4, 4), new float[]{1, 2, 3, 4,  5, 6, 7, 8,  9, 10, 11, 12,  13, 14, 15, 16}, 1);
            for (int i = 0; i < 16; i++) assertEq(i + 1, ubo.get(i), "[" + i + "]");
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void mat3Array_perElementStride48_eachMatrixGetsItsOwnPadding() {
        final FloatBuffer ubo = outBuf(24);
        try {
            Std140Writer.write(ubo, array(/*off*/ 0, /*stride*/ 48, /*len*/ 2, 3, 3),
                new float[]{
                    1, 2, 3, 4, 5, 6, 7, 8, 9,        // matrix 0 (col-major packed)
                    10, 20, 30, 40, 50, 60, 70, 80, 90 // matrix 1
                }, 2);
            assertEq(1f, ubo.get(0)); assertEq(2f, ubo.get(1)); assertEq(3f, ubo.get(2)); assertEq(0f, ubo.get(3));
            assertEq(4f, ubo.get(4)); assertEq(5f, ubo.get(5)); assertEq(6f, ubo.get(6)); assertEq(0f, ubo.get(7));
            assertEq(7f, ubo.get(8)); assertEq(8f, ubo.get(9)); assertEq(9f, ubo.get(10)); assertEq(0f, ubo.get(11));
            assertEq(10f, ubo.get(12)); assertEq(20f, ubo.get(13)); assertEq(30f, ubo.get(14)); assertEq(0f, ubo.get(15));
            assertEq(40f, ubo.get(16)); assertEq(50f, ubo.get(17)); assertEq(60f, ubo.get(18)); assertEq(0f, ubo.get(19));
            assertEq(70f, ubo.get(20)); assertEq(80f, ubo.get(21)); assertEq(90f, ubo.get(22)); assertEq(0f, ubo.get(23));
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void mat4Array_perElementStride64() {
        final FloatBuffer ubo = outBuf(32);
        try {
            final float[] data = new float[32];
            for (int i = 0; i < 32; i++) data[i] = i;
            Std140Writer.write(ubo, array(/*off*/ 0, /*stride*/ 64, /*len*/ 2, 4, 4), data, 2);
            for (int i = 0; i < 32; i++) assertEq(i, ubo.get(i), "[" + i + "]");
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
    void srcCount_clampedToArrayLen() {
        final FloatBuffer ubo = outBuf(16);
        try {
            Std140Writer.write(ubo, array(/*off*/ 0, /*stride*/ 16, /*len*/ 2, 1, 1), new float[]{1, 2, 3, 4}, 4);
            assertEq(1f, ubo.get(0));
            assertEq(2f, ubo.get(4));
            assertEq(0f, ubo.get(8));   // not written
            assertEq(0f, ubo.get(12));  // not written
        } finally {
            MemoryUtil.memFree(ubo);
        }
    }

    @Test
    void srcCount_clampedToSrcSize() {
        final FloatBuffer ubo = outBuf(16);
        try {
            Std140Writer.write(ubo, array(/*off*/ 0, /*stride*/ 16, /*len*/ 4, 1, 1), new float[]{7, 8}, 4);
            assertEq(7f, ubo.get(0));
            assertEq(8f, ubo.get(4));
            assertEq(0f, ubo.get(8));   // beyond src
            assertEq(0f, ubo.get(12));
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
