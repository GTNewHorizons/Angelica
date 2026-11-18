package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScissorClampTest {

    private static Arguments rect(String name, int x, int y, int w, int h, int targetW, int targetH, boolean nonEmpty, int... expectedOut) {
        return Arguments.of(name, x, y, w, h, targetW, targetH, nonEmpty, expectedOut);
    }

    /** No expected rect: only the return value is specified for that input. */
    static Stream<Arguments> rects() {
        return Stream.of(
            rect("inside the target: unchanged", 4, 8, 16, 32, 64, 96, true, 4, 8, 16, 32),
            rect("overhang past the right edge shrinks", 0, 0, 1710, 960, 1708, 960, true, 0, 0, 1708, 960),
            rect("overhang past the right and bottom edges shrinks", 60, 90, 16, 32, 64, 96, true, 60, 90, 4, 6),
            rect("negative origin shrinks instead of shifting", -6, -10, 16, 32, 64, 96, true, 0, 0, 10, 22),
            rect("straddling both edges clamps to the whole target", -8, -8, 128, 128, 64, 96, true, 0, 0, 64, 96),
            rect("fully right of the target: empty", 64, 0, 16, 32, 64, 96, false, 64, 0, 0, 32),
            rect("fully left of the target: empty", -40, 0, 16, 32, 64, 96, false, 0, 0, 0, 32),
            rect("zero-width source: empty", 0, 0, 0, 32, 64, 96, false),
            rect("negative-height source: empty", 0, 0, 16, -4, 64, 96, false),
            rect("unknown target size leaves the rect alone", -6, 4, 1710, 32, 0, 0, true, -6, 4, 1710, 32),
            rect("unknown target width with a zero-width source: empty", 0, 0, 0, 32, 0, 96, false),
            rect("large rect does not overflow", 0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, 64, 96, true, 0, 0, 64, 96));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rects")
    void clamp(String name, int x, int y, int w, int h, int targetW, int targetH, boolean nonEmpty, int[] expectedOut) {
        final int[] out = new int[4];
        assertEquals(nonEmpty, ScissorClamp.clamp(x, y, w, h, targetW, targetH, out));
        if (expectedOut.length != 0) assertArrayEquals(expectedOut, out);
    }
}
