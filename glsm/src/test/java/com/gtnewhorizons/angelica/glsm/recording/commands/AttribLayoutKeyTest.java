package com.gtnewhorizons.angelica.glsm.recording.commands;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AttribLayoutKeyTest {

    private static int[] offsetsOf(AttribLayoutKey k) {
        final int[] out = new int[k.locations.length];
        for (int i = 0; i < out.length; i++) out[i] = k.offset(i);
        return out;
    }

    @Test
    void standardPosColorUvNormal_isAlignedAndPacked() {
        final AttribLayoutKey k = new AttribLayoutKey(
            new int[]{0, 1, 2, 4},
            new int[]{3, 4, 2, 3},
            new int[]{GL11.GL_FLOAT, GL11.GL_UNSIGNED_BYTE, GL11.GL_FLOAT, GL11.GL_BYTE},
            new boolean[]{false, true, false, true});
        assertArrayEquals(new int[]{0, 12, 16, 24}, offsetsOf(k));
        assertEquals(28, k.stride());
    }

    @Test
    void posUvNormal3_roundsTailStrideTo24() {
        final AttribLayoutKey k = new AttribLayoutKey(
            new int[]{0, 2, 4},
            new int[]{3, 2, 3},
            new int[]{GL11.GL_FLOAT, GL11.GL_FLOAT, GL11.GL_BYTE},
            new boolean[]{false, false, true});
        assertArrayEquals(new int[]{0, 12, 20}, offsetsOf(k));
        assertEquals(24, k.stride());
    }

    @Test
    void interiorHazardOrder_roundsMidAttribOffset() {
        final AttribLayoutKey k = new AttribLayoutKey(
            new int[]{0, 4, 2},
            new int[]{3, 3, 2},
            new int[]{GL11.GL_FLOAT, GL11.GL_BYTE, GL11.GL_FLOAT},
            new boolean[]{false, true, false});
        assertArrayEquals(new int[]{0, 12, 16}, offsetsOf(k));
        assertEquals(24, k.stride());
    }

    @Test
    void allByteLayout_packsTight() {
        final AttribLayoutKey k = new AttribLayoutKey(
            new int[]{0, 1, 2},
            new int[]{3, 4, 2},
            new int[]{GL11.GL_BYTE, GL11.GL_UNSIGNED_BYTE, GL11.GL_BYTE},
            new boolean[]{true, true, false});
        assertArrayEquals(new int[]{0, 3, 7}, offsetsOf(k));
        assertEquals(9, k.stride());
    }

    @Test
    void shortsFollowingByte_roundToEvenAndStrideToEven() {
        final AttribLayoutKey k = new AttribLayoutKey(
            new int[]{0, 1},
            new int[]{1, 2},
            new int[]{GL11.GL_BYTE, GL11.GL_SHORT},
            new boolean[]{true, false});
        assertArrayEquals(new int[]{0, 2}, offsetsOf(k));
        assertEquals(6, k.stride());
    }

    @Test
    void equalInputs_produceEqualKeys() {
        final AttribLayoutKey a = new AttribLayoutKey(
            new int[]{0, 2}, new int[]{3, 2},
            new int[]{GL11.GL_FLOAT, GL11.GL_FLOAT}, new boolean[]{false, false});
        final AttribLayoutKey b = new AttribLayoutKey(
            new int[]{0, 2}, new int[]{3, 2},
            new int[]{GL11.GL_FLOAT, GL11.GL_FLOAT}, new boolean[]{false, false});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.stride(), b.stride());
        assertArrayEquals(offsetsOf(a), offsetsOf(b));
    }

    @Test
    void differentNormalized_producesDifferentKeys() {
        final AttribLayoutKey a = new AttribLayoutKey(
            new int[]{0}, new int[]{4}, new int[]{GL11.GL_UNSIGNED_BYTE}, new boolean[]{true});
        final AttribLayoutKey b = new AttribLayoutKey(
            new int[]{0}, new int[]{4}, new int[]{GL11.GL_UNSIGNED_BYTE}, new boolean[]{false});
        assertNotEquals(a, b);
    }
}
