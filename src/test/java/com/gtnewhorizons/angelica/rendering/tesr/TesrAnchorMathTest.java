package com.gtnewhorizons.angelica.rendering.tesr;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

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
    void anchorCoordRoundsDownToGrid() {
        assertEquals(96, TesrAnchorMath.anchorCoord(100.5));
        assertEquals(112, TesrAnchorMath.anchorCoord(112.0));
        assertEquals(-16, TesrAnchorMath.anchorCoord(-0.5));
        assertEquals(-32, TesrAnchorMath.anchorCoord(-16.5));
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
    }

    @Test
    void hashIsInvariantUnderCameraMotion() {
        final double camAX = TE_X - 20.0, camAY = TE_Y - 3.0, camAZ = TE_Z + 8.0;
        final long ax = TesrAnchorMath.anchorCoord(camAX);
        final long ay = TesrAnchorMath.anchorCoord(camAY);
        final long az = TesrAnchorMath.anchorCoord(camAZ);

        final Matrix4f mA = anchorRelative(camAX, camAY, camAZ, ax, ay, az);
        final long h1 = TesrAnchorMath.instanceHash(42, mA, 0x00F000F0, -1);

        final double camBX = camAX + 137.37, camBY = camAY + 41.03, camBZ = camAZ - 250.91;
        final Matrix4f mB = anchorRelative(camBX, camBY, camBZ, ax, ay, az);
        final long h2 = TesrAnchorMath.instanceHash(42, mB, 0x00F000F0, -1);

        assertEquals(h1, h2, "camera-only motion must not change the rebuild hash");
    }

    @Test
    void reanchorRoundTripKeepsWorldPosition() {
        final double camX = TE_X - 20.0, camY = TE_Y, camZ = TE_Z + 20.0;
        final long ax1 = TesrAnchorMath.anchorCoord(camX);
        final long ax2 = ax1 + 480;
        final Matrix4f m1 = anchorRelative(camX, camY, camZ, ax1, 0, 0);
        final Matrix4f m2 = anchorRelative(camX, camY, camZ, ax2, 0, 0);
        assertEquals(ax1 + m1.m30(), ax2 + m2.m30(), 1e-2);
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
    }

    @Test
    void summedHashIsOrderIndependent() {
        final Matrix4f m1 = new Matrix4f().translation(1f, 2f, 3f);
        final Matrix4f m2 = new Matrix4f().translation(-4f, 5f, -6f).rotateY(0.5f);
        final long a = TesrAnchorMath.instanceHash(1, m1, 10, -1);
        final long b = TesrAnchorMath.instanceHash(2, m2, 20, -1);
        assertEquals(a + b, b + a);
        assertNotEquals(a, b);
    }
}
