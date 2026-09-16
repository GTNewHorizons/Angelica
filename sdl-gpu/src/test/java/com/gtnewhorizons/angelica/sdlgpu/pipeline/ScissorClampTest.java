package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScissorClampTest {

    private final int[] out = new int[4];

    @Test
    void rectInsideTheTargetIsUnchanged() {
        assertTrue(ScissorClamp.clamp(4, 8, 16, 32, 64, 96, out));
        assertArrayEquals(new int[]{4, 8, 16, 32}, out);
    }

    @Test
    void overhangPastTheRightAndBottomEdgesShrinks() {
        assertTrue(ScissorClamp.clamp(0, 0, 1710, 960, 1708, 960, out));
        assertArrayEquals(new int[]{0, 0, 1708, 960}, out);

        assertTrue(ScissorClamp.clamp(60, 90, 16, 32, 64, 96, out));
        assertArrayEquals(new int[]{60, 90, 4, 6}, out);
    }

    @Test
    void negativeOriginShrinksTheRectInsteadOfShiftingIt() {
        assertTrue(ScissorClamp.clamp(-6, -10, 16, 32, 64, 96, out));
        assertArrayEquals(new int[]{0, 0, 10, 22}, out);
    }

    @Test
    void rectStraddlingBothEdgesIsClampedToTheWholeTarget() {
        assertTrue(ScissorClamp.clamp(-8, -8, 128, 128, 64, 96, out));
        assertArrayEquals(new int[]{0, 0, 64, 96}, out);
    }

    @Test
    void rectFullyOutsideTheTargetIsEmpty() {
        assertFalse(ScissorClamp.clamp(64, 0, 16, 32, 64, 96, out));
        assertArrayEquals(new int[]{64, 0, 0, 32}, out);

        assertFalse(ScissorClamp.clamp(-40, 0, 16, 32, 64, 96, out));
        assertArrayEquals(new int[]{0, 0, 0, 32}, out);
    }

    @Test
    void degenerateSourceRectIsEmpty() {
        assertFalse(ScissorClamp.clamp(0, 0, 0, 32, 64, 96, out));
        assertFalse(ScissorClamp.clamp(0, 0, 16, -4, 64, 96, out));
    }

    @Test
    void unknownTargetSizeLeavesTheRectAlone() {
        assertTrue(ScissorClamp.clamp(-6, 4, 1710, 32, 0, 0, out));
        assertArrayEquals(new int[]{-6, 4, 1710, 32}, out);

        assertFalse(ScissorClamp.clamp(0, 0, 0, 32, 0, 96, out));
    }

    @Test
    void largeRectDoesNotOverflow() {
        assertTrue(ScissorClamp.clamp(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, 64, 96, out));
        assertArrayEquals(new int[]{0, 0, 64, 96}, out);
    }
}
