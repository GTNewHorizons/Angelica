package com.gtnewhorizons.angelica.glsm.states;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixelStoreStateTest {

    @Test
    void defaults() {
        assertTrue(PixelStoreState.DEFAULT.isDefault());
        assertEquals(4, PixelStoreState.DEFAULT.alignment());
        assertEquals(0, PixelStoreState.DEFAULT.rowLength());
        assertFalse(PixelStoreState.DEFAULT.swapBytes());
        assertFalse(PixelStoreState.DEFAULT.lsbFirst());
    }

    @Test
    void withUpdatesUnpackParams() {
        PixelStoreState s = PixelStoreState.DEFAULT
            .with(GL11.GL_UNPACK_ALIGNMENT, 1)
            .with(GL11.GL_UNPACK_ROW_LENGTH, 64)
            .with(GL11.GL_UNPACK_SKIP_ROWS, 2)
            .with(GL11.GL_UNPACK_SKIP_PIXELS, 3)
            .with(GL12.GL_UNPACK_IMAGE_HEIGHT, 32)
            .with(GL12.GL_UNPACK_SKIP_IMAGES, 1)
            .with(GL11.GL_UNPACK_SWAP_BYTES, 1)
            .with(GL11.GL_UNPACK_LSB_FIRST, 1);
        assertEquals(new PixelStoreState(1, 64, 2, 3, 32, 1, true, true), s);
        assertFalse(s.isDefault());
    }

    @Test
    void invalidValuesIgnored() {
        assertSame(PixelStoreState.DEFAULT, PixelStoreState.DEFAULT.with(GL11.GL_UNPACK_ALIGNMENT, 3));
        assertSame(PixelStoreState.DEFAULT, PixelStoreState.DEFAULT.with(GL11.GL_UNPACK_ALIGNMENT, 0));
        assertSame(PixelStoreState.DEFAULT, PixelStoreState.DEFAULT.with(GL11.GL_UNPACK_ROW_LENGTH, -1));
        assertSame(PixelStoreState.DEFAULT, PixelStoreState.DEFAULT.with(GL11.GL_UNPACK_SKIP_ROWS, -5));
        assertSame(PixelStoreState.DEFAULT, PixelStoreState.DEFAULT.with(GL12.GL_UNPACK_IMAGE_HEIGHT, -1));
    }

    @Test
    void booleansAcceptAnyNonzero() {
        assertTrue(PixelStoreState.DEFAULT.with(GL11.GL_UNPACK_SWAP_BYTES, 2).swapBytes());
        assertTrue(PixelStoreState.DEFAULT.with(GL11.GL_UNPACK_LSB_FIRST, -1).lsbFirst());
        PixelStoreState on = PixelStoreState.DEFAULT.with(GL11.GL_UNPACK_SWAP_BYTES, 1);
        assertFalse(on.with(GL11.GL_UNPACK_SWAP_BYTES, 0).swapBytes());
    }

    @Test
    void packPnamesSetTheSameFields() {
        PixelStoreState s = PixelStoreState.DEFAULT
            .with(GL11.GL_PACK_ALIGNMENT, 1)
            .with(GL11.GL_PACK_ROW_LENGTH, 64)
            .with(GL11.GL_PACK_SKIP_ROWS, 2)
            .with(GL11.GL_PACK_SKIP_PIXELS, 3)
            .with(GL12.GL_PACK_IMAGE_HEIGHT, 32)
            .with(GL12.GL_PACK_SKIP_IMAGES, 1)
            .with(GL11.GL_PACK_SWAP_BYTES, 1)
            .with(GL11.GL_PACK_LSB_FIRST, 1);
        assertEquals(new PixelStoreState(1, 64, 2, 3, 32, 1, true, true), s);
        assertSame(PixelStoreState.DEFAULT, PixelStoreState.DEFAULT.with(GL11.GL_PACK_ALIGNMENT, 3));
        assertSame(PixelStoreState.DEFAULT, PixelStoreState.DEFAULT.with(GL11.GL_PACK_ROW_LENGTH, -1));
    }

    @Test
    void unknownPnamesIgnored() {
        assertSame(PixelStoreState.DEFAULT, PixelStoreState.DEFAULT.with(0x1234, 1));
    }

    @Test
    void unchangedValueReturnsSameInstance() {
        final PixelStoreState s = new PixelStoreState(1, 64, 2, 3, 32, 1, true, true);
        assertSame(s, s.with(GL11.GL_UNPACK_ALIGNMENT, 1));
        assertSame(s, s.with(GL11.GL_PACK_ALIGNMENT, 1));
        assertSame(s, s.with(GL11.GL_UNPACK_ROW_LENGTH, 64));
        assertSame(s, s.with(GL11.GL_PACK_ROW_LENGTH, 64));
        assertSame(s, s.with(GL11.GL_UNPACK_SKIP_ROWS, 2));
        assertSame(s, s.with(GL11.GL_PACK_SKIP_ROWS, 2));
        assertSame(s, s.with(GL11.GL_UNPACK_SKIP_PIXELS, 3));
        assertSame(s, s.with(GL11.GL_PACK_SKIP_PIXELS, 3));
        assertSame(s, s.with(GL12.GL_UNPACK_IMAGE_HEIGHT, 32));
        assertSame(s, s.with(GL12.GL_PACK_IMAGE_HEIGHT, 32));
        assertSame(s, s.with(GL12.GL_UNPACK_SKIP_IMAGES, 1));
        assertSame(s, s.with(GL12.GL_PACK_SKIP_IMAGES, 1));
        assertSame(s, s.with(GL11.GL_UNPACK_SWAP_BYTES, 5));
        assertSame(s, s.with(GL11.GL_PACK_SWAP_BYTES, 5));
        assertSame(s, s.with(GL11.GL_UNPACK_LSB_FIRST, 1));
        assertSame(s, s.with(GL11.GL_PACK_LSB_FIRST, 1));
    }

    @Test
    void isPackSeparatesTheFamilies() {
        final int[] pack = {
            GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS,
            GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST,
        };
        final int[] unpack = {
            GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS,
            GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES, GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_UNPACK_LSB_FIRST,
        };
        for (final int pname : pack) {
            assertTrue(PixelStoreState.isPack(pname));
        }
        for (final int pname : unpack) {
            assertFalse(PixelStoreState.isPack(pname));
        }
        assertFalse(PixelStoreState.isPack(0x1234));
    }
}
