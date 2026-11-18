package com.gtnewhorizons.angelica.glsm.recording.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AttribLayoutKeyTest {

    private static int[] offsetsOf(AttribLayoutKey k) {
        final int[] out = new int[k.locations.length];
        for (int i = 0; i < out.length; i++) out[i] = k.offset(i);
        return out;
    }

    static Stream<Arguments> layouts() {
        return Stream.of(
            Arguments.of("standard pos/color/uv/normal is aligned and packed",
                new AttribLayoutKey(
                    new int[]{0, 1, 2, 4},
                    new int[]{3, 4, 2, 3},
                    new int[]{GL11.GL_FLOAT, GL11.GL_UNSIGNED_BYTE, GL11.GL_FLOAT, GL11.GL_BYTE},
                    new boolean[]{false, true, false, true}),
                new int[]{0, 12, 16, 24}, 28),
            Arguments.of("pos/uv/normal3 rounds the tail stride to 24",
                new AttribLayoutKey(
                    new int[]{0, 2, 4},
                    new int[]{3, 2, 3},
                    new int[]{GL11.GL_FLOAT, GL11.GL_FLOAT, GL11.GL_BYTE},
                    new boolean[]{false, false, true}),
                new int[]{0, 12, 20}, 24),
            Arguments.of("byte attrib in the middle rounds the next offset",
                new AttribLayoutKey(
                    new int[]{0, 4, 2},
                    new int[]{3, 3, 2},
                    new int[]{GL11.GL_FLOAT, GL11.GL_BYTE, GL11.GL_FLOAT},
                    new boolean[]{false, true, false}),
                new int[]{0, 12, 16}, 24),
            Arguments.of("all-byte layout packs tight",
                new AttribLayoutKey(
                    new int[]{0, 1, 2},
                    new int[]{3, 4, 2},
                    new int[]{GL11.GL_BYTE, GL11.GL_UNSIGNED_BYTE, GL11.GL_BYTE},
                    new boolean[]{true, true, false}),
                new int[]{0, 3, 7}, 9),
            Arguments.of("shorts after a byte round offset and stride to even",
                new AttribLayoutKey(
                    new int[]{0, 1},
                    new int[]{1, 2},
                    new int[]{GL11.GL_BYTE, GL11.GL_SHORT},
                    new boolean[]{true, false}),
                new int[]{0, 2}, 6));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layouts")
    void offsetsAndStride(String name, AttribLayoutKey key, int[] offsets, int stride) {
        assertArrayEquals(offsets, offsetsOf(key));
        assertEquals(stride, key.stride());
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
