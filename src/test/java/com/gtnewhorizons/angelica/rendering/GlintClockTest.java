package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlintClockTest {

    @BeforeEach
    void resetArmorPathTable() {
        Reflect.setStatic(ArmorTexturePaths.class, "table", new String[0]);
    }

    private static float armorU0() {
        return Reflect.<Float>getStatic(GlintClock.class, "armorU0");
    }

    private static float armorV0() {
        return Reflect.<Float>getStatic(GlintClock.class, "armorV0");
    }

    private static float armorU1() {
        return Reflect.<Float>getStatic(GlintClock.class, "armorU1");
    }

    private static float armorV1() {
        return Reflect.<Float>getStatic(GlintClock.class, "armorV1");
    }

    @Test
    void armorTranslationMatchesVanillaModuloIntegerUv() {
        for (final long millis : new long[] { 61725L, 1000003L }) {
            GlintClock.beginFrame(millis);
            final double ticks = millis / 50.0;
            for (int k = 0; k <= 1; k++) {
                final double speed = 0.001 + 0.003 * k;
                final float f11 = (float) (ticks * speed * 20.0);
                final float angle = (float) Math.toRadians(30 - 60 * k);
                final Matrix4f vanilla = new Matrix4f().scale(0.33333334f).rotateZ(angle).translate(0f, f11, 0f);
                final float u = k == 0 ? armorU0() : armorU1();
                final float v = k == 0 ? armorV0() : armorV1();
                final Matrix4f ours = new Matrix4f().scale(0.33333334f).rotateZ(angle).translate(u, v, 0f);

                final String ctx = "millis=" + millis + " k=" + k;
                assertEquals(vanilla.m00(), ours.m00(), 1e-6f, ctx + " scale/rotate must be untouched");
                assertEquals(vanilla.m01(), ours.m01(), 1e-6f, ctx + " scale/rotate must be untouched");
                assertEquals(vanilla.m10(), ours.m10(), 1e-6f, ctx + " scale/rotate must be untouched");
                assertEquals(vanilla.m11(), ours.m11(), 1e-6f, ctx + " scale/rotate must be untouched");

                final float dx = vanilla.m30() - ours.m30();
                final float dy = vanilla.m31() - ours.m31();
                assertEquals(0f, dx - Math.round(dx), 1e-3f, ctx + " u must differ from vanilla by a whole texel");
                assertEquals(0f, dy - Math.round(dy), 1e-3f, ctx + " v must differ from vanilla by a whole texel");
            }
        }
    }

    @Test
    void beginFrameIsDeterministicAndBounded() {
        GlintClock.beginFrame(864000000123L);
        final float u0 = armorU0();
        final float v0 = armorV0();
        final float u1 = armorU1();
        final float v1 = armorV1();

        final float bound = 4.25f;
        assertTrue(Math.abs(u0) <= bound, "u0=" + u0);
        assertTrue(Math.abs(v0) <= bound, "v0=" + v0);
        assertTrue(Math.abs(u1) <= bound, "u1=" + u1);
        assertTrue(Math.abs(v1) <= bound, "v1=" + v1);
    }

    @Test
    void secondArmorLayerFollowsRotationSign() {
        final Matrix4f first = new Matrix4f().scale(1f / 3f).rotateZ((float) Math.toRadians(30.0));
        final Matrix4f second = new Matrix4f().scale(1f / 3f).rotateZ((float) Math.toRadians(-30.0));

        assertFalse(GlintClock.secondArmorLayer(first), "k=0 (rotate +30) must read as the first armor layer");
        assertTrue(GlintClock.secondArmorLayer(second), "k=1 (rotate -30) must read as the second armor layer");
    }

    @Test
    void pathsMatchForgeArmorResourceLiterals() {
        assertEquals("textures/models/armor/diamond_layer_2_overlay.png", ArmorTexturePaths.path("diamond", 3, 2, "overlay"));
        assertEquals("textures/models/armor/diamond_layer_2.png", ArmorTexturePaths.path("diamond", 3, 2, null));
        assertEquals("textures/models/armor/iron_layer_1.png", ArmorTexturePaths.path("iron", 2, 0, null));
        assertEquals("textures/models/armor/iron_layer_1_overlay.png", ArmorTexturePaths.path("iron", 2, 1, "overlay"));
        assertEquals("textures/models/armor/chainmail_layer_1.png", ArmorTexturePaths.path("chainmail", 1, 3, null));
        assertEquals("textures/models/armor/cloth_layer_1_x.png", ArmorTexturePaths.path("cloth", 0, 0, "x"));
        assertEquals("textures/models/armor/null_layer_1.png", ArmorTexturePaths.path(null, 2, 1, null));
        assertEquals("textures/models/armor/null_layer_2_overlay.png", ArmorTexturePaths.path(null, 2, 2, "overlay"));
    }

    @Test
    void replacedPrefixElementRebuildsRow() {
        final String second = ArmorTexturePaths.path("skeleton", 3, 0, null);
        final String third = ArmorTexturePaths.path("wither_skeleton", 3, 0, null);
        assertNotEquals(second, third);
    }

    @Test
    void aChangedFloatGetsANewBox() {
        final Object prev = Float.valueOf(1.5f);
        assertSame(prev, OperationArgs.boxed(prev, 1.5f));
        final Object next = OperationArgs.boxed(prev, 2.5f);
        assertNotSame(prev, next);
        assertEquals(2.5f, (Float) next);
    }

    @Test
    void aChangedIntGetsANewBox() {
        final Object prev = Integer.valueOf(100_000);
        assertSame(prev, OperationArgs.boxed(prev, 100_000));
        final Object next = OperationArgs.boxed(prev, 100_001);
        assertNotSame(prev, next);
        assertEquals(100_001, (Integer) next);
    }
}
