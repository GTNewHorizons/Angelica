package com.gtnewhorizons.angelica.glsm.states;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixelUnpackStateTest {

    @Test
    void defaults() {
        assertTrue(PixelUnpackState.DEFAULT.isDefault());
        assertEquals(4, PixelUnpackState.DEFAULT.alignment());
        assertEquals(0, PixelUnpackState.DEFAULT.rowLength());
        assertFalse(PixelUnpackState.DEFAULT.swapBytes());
        assertFalse(PixelUnpackState.DEFAULT.lsbFirst());
    }

    @Test
    void withUpdatesUnpackParams() {
        PixelUnpackState s = PixelUnpackState.DEFAULT
            .with(GL11.GL_UNPACK_ALIGNMENT, 1)
            .with(GL11.GL_UNPACK_ROW_LENGTH, 64)
            .with(GL11.GL_UNPACK_SKIP_ROWS, 2)
            .with(GL11.GL_UNPACK_SKIP_PIXELS, 3)
            .with(GL12.GL_UNPACK_IMAGE_HEIGHT, 32)
            .with(GL12.GL_UNPACK_SKIP_IMAGES, 1)
            .with(GL11.GL_UNPACK_SWAP_BYTES, 1)
            .with(GL11.GL_UNPACK_LSB_FIRST, 1);
        assertEquals(new PixelUnpackState(1, 64, 2, 3, 32, 1, true, true), s);
        assertFalse(s.isDefault());
    }

    @Test
    void invalidValuesIgnored() {
        assertSame(PixelUnpackState.DEFAULT, PixelUnpackState.DEFAULT.with(GL11.GL_UNPACK_ALIGNMENT, 3));
        assertSame(PixelUnpackState.DEFAULT, PixelUnpackState.DEFAULT.with(GL11.GL_UNPACK_ALIGNMENT, 0));
        assertSame(PixelUnpackState.DEFAULT, PixelUnpackState.DEFAULT.with(GL11.GL_UNPACK_ROW_LENGTH, -1));
        assertSame(PixelUnpackState.DEFAULT, PixelUnpackState.DEFAULT.with(GL11.GL_UNPACK_SKIP_ROWS, -5));
        assertSame(PixelUnpackState.DEFAULT, PixelUnpackState.DEFAULT.with(GL12.GL_UNPACK_IMAGE_HEIGHT, -1));
    }

    @Test
    void booleansAcceptAnyNonzero() {
        assertTrue(PixelUnpackState.DEFAULT.with(GL11.GL_UNPACK_SWAP_BYTES, 2).swapBytes());
        assertTrue(PixelUnpackState.DEFAULT.with(GL11.GL_UNPACK_LSB_FIRST, -1).lsbFirst());
        PixelUnpackState on = PixelUnpackState.DEFAULT.with(GL11.GL_UNPACK_SWAP_BYTES, 1);
        assertFalse(on.with(GL11.GL_UNPACK_SWAP_BYTES, 0).swapBytes());
    }

    @Test
    void packAndUnknownPnamesIgnored() {
        assertSame(PixelUnpackState.DEFAULT, PixelUnpackState.DEFAULT.with(GL11.GL_PACK_ALIGNMENT, 1));
        assertSame(PixelUnpackState.DEFAULT, PixelUnpackState.DEFAULT.with(GL11.GL_PACK_ROW_LENGTH, 16));
        assertSame(PixelUnpackState.DEFAULT, PixelUnpackState.DEFAULT.with(0x1234, 1));
    }
}
