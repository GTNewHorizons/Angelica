package com.gtnewhorizons.angelica.rendering.tesr;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TesrAnchorMathTest {

    private static final double TE_X = 123456.25, TE_Y = 64.0, TE_Z = -98765.75;

    private static Matrix4f cameraRelative(double camX, double camY, double camZ) {
        return new Matrix4f().translation((float) (TE_X - camX), (float) (TE_Y - camY), (float) (TE_Z - camZ));
    }

    private static Matrix4f anchorRelative(double camX, double camY, double camZ, long ax, long ay, long az) {
        final Matrix4f m = cameraRelative(camX, camY, camZ);
        TesrAnchorMath.toAnchorRelative(m, camX, camY, camZ, ax, ay, az);
        return m;
    }

    @Test
    void shouldReanchorTriggersOnlyBeyondThreshold() {
        assertFalse(TesrAnchorMath.shouldReanchor(100, 0, 0, 0, 0, 0));
        assertFalse(TesrAnchorMath.shouldReanchor(511, 0, 0, 0, 0, 0));
        assertTrue(TesrAnchorMath.shouldReanchor(513, 0, 0, 0, 0, 0));
        assertTrue(TesrAnchorMath.shouldReanchor(300, 300, 300, 0, 0, 0));
    }

    @Test
    void anchorRelativeTranslationReconstructsWorldOffset() {
        final double camX = TE_X - 30.5, camY = TE_Y + 5.0, camZ = TE_Z + 12.25;
        final long ax = TesrAnchorMath.anchorCoord(camX);
        final long ay = TesrAnchorMath.anchorCoord(camY);
        final long az = TesrAnchorMath.anchorCoord(camZ);
        final Matrix4f mA = anchorRelative(camX, camY, camZ, ax, ay, az);
        assertEquals(TE_X - ax, mA.m30(), 1e-2f);
        assertEquals(TE_Y - ay, mA.m31(), 1e-2f);
        assertEquals(TE_Z - az, mA.m32(), 1e-2f);

        final Matrix4f moved = anchorRelative(camX + 137.37, camY + 41.03, camZ - 250.91, ax, ay, az);
        assertEquals(TE_X - ax, moved.m30(), 1e-2f);
        assertEquals(TE_Y - ay, moved.m31(), 1e-2f);
        assertEquals(TE_Z - az, moved.m32(), 1e-2f);
    }

    static Stream<Arguments> texMatrices() {
        final Matrix4f scroll = new Matrix4f().m00(0.9f).m01(0.1f).m10(-0.2f).m11(0.8f).m30(0.25f).m31(0.5f);
        final Matrix4f zeros = new Matrix4f().zero();
        return Stream.of(
            Arguments.of("absent vs absent", null, null, true),
            Arguments.of("absent vs zeros", null, zeros, false),
            Arguments.of("scroll vs scroll", scroll, scroll, true),
            Arguments.of("scroll vs absent", scroll, null, false),
            Arguments.of("m00 off", scroll, new Matrix4f(scroll).m00(0.91f), false),
            Arguments.of("m01 off", scroll, new Matrix4f(scroll).m01(0.11f), false),
            Arguments.of("m10 off", scroll, new Matrix4f(scroll).m10(-0.21f), false),
            Arguments.of("m11 off", scroll, new Matrix4f(scroll).m11(0.81f), false),
            Arguments.of("m30 off", scroll, new Matrix4f(scroll).m30(0.26f), false),
            Arguments.of("m31 off", scroll, new Matrix4f(scroll).m31(0.51f), false),
            Arguments.of("zeros vs zeros", zeros, zeros, true),
            Arguments.of("zeros vs absent", zeros, null, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("texMatrices")
    void texMatrixCompareSeparatesAbsentFromPresent(String name, Matrix4f stored, Matrix4f query, boolean same) {
        final float[] terms = new float[2 * TesrAnchorMath.TEX_MATRIX_TERMS];
        TesrAnchorMath.storeTexMatrix(terms, TesrAnchorMath.TEX_MATRIX_TERMS, stored);
        assertEquals(same, TesrAnchorMath.sameTexMatrix(terms, TesrAnchorMath.TEX_MATRIX_TERMS, query));
    }

    @Test
    void hashChangesOnRealDifferences() {
        final Matrix4f mA = new Matrix4f().translation(1.5f, 2.5f, 3.5f);
        final long base = TesrAnchorMath.instanceHash(42, mA, 0x00F000F0, -1);
        assertNotEquals(base, TesrAnchorMath.instanceHash(43, mA, 0x00F000F0, -1), "template identity");
        assertNotEquals(base, TesrAnchorMath.instanceHash(42, mA, 0x00F000A0, -1), "packed light");
        assertNotEquals(base, TesrAnchorMath.instanceHash(42, mA, 0x00F000F0, 0xFF0000FF), "color");
        final Matrix4f moved = new Matrix4f(mA).translate(1f, 0f, 0f);
        assertNotEquals(base, TesrAnchorMath.instanceHash(42, moved, 0x00F000F0, -1), "transform");
        assertNotEquals(TesrAnchorMath.texMatrixHash(null), TesrAnchorMath.texMatrixHash(new Matrix4f().translation(0.5f, 0f, 0f)), "tex matrix");
    }
}
