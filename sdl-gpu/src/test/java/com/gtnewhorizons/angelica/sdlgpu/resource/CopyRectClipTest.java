package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopyRectClipTest {

    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of("insideBothImages", 5, 9, 3, 2, 7, 4, 64, 64, 64, 64, false, 5, 9, 3, 2, 7, 4),
            Arguments.of("negativeSourceOrigin", -3, -2, 4, 6, 10, 8, 64, 96, 64, 64, false, 0, 0, 7, 8, 7, 6),
            Arguments.of("negativeDestinationOrigin", 10, 10, -4, -6, 20, 20, 64, 64, 64, 64, false, 14, 16, 0, 0, 16, 14),
            Arguments.of("farSourceEdge", 60, 60, 0, 0, 10, 10, 64, 64, 64, 64, false, 60, 60, 0, 0, 4, 4),
            Arguments.of("farDestinationEdge", 0, 0, 28, 30, 16, 16, 64, 64, 32, 32, false, 0, 0, 28, 30, 4, 2),
            Arguments.of("oversizeBothImages", 61, 93, 62, 61, 10, 9, 64, 96, 64, 64, false, 61, 93, 62, 61, 2, 3),
            Arguments.of("oversizeNearFarEdge", 60, 93, 61, 62, 10, 9, 64, 96, 64, 64, false, 60, 93, 61, 62, 3, 2),
            Arguments.of("pastSourceEdge", 64, 0, 0, 0, 8, 8, 64, 64, 64, 64, true, 0, 0, 0, 0, 0, 0),
            Arguments.of("beforeSourceOrigin", -20, 0, 0, 0, 8, 8, 64, 64, 64, 64, true, 0, 0, 0, 0, 0, 0),
            Arguments.of("pastDestinationEdge", 0, 0, 32, 0, 8, 8, 64, 64, 32, 32, true, 0, 0, 0, 0, 0, 0),
            Arguments.of("zeroWidth", 0, 0, 0, 0, 0, 8, 64, 64, 64, 64, true, 0, 0, 0, 0, 0, 0),
            Arguments.of("zeroHeight", 0, 0, 0, 0, 8, 0, 64, 64, 64, 64, true, 0, 0, 0, 0, 0, 0),
            Arguments.of("negativeWidth", 0, 0, 0, 0, -4, 8, 64, 64, 64, 64, true, 0, 0, 0, 0, 0, 0),
            Arguments.of("unknownDestinationExtents", 0, 0, 200, 200, 8, 8, 64, 64, 0, 0, false, 0, 0, 200, 200, 8, 8),
            Arguments.of("unknownSourceExtents", 200, 200, 0, 0, 8, 8, 0, 0, 64, 64, false, 200, 200, 0, 0, 8, 8),
            Arguments.of("mipLevelSizedDestination", 0, 0, 0, 0, 64, 64, 64, 64, 32, 32, false, 0, 0, 0, 0, 32, 32));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void clipsAsExpected(String label, int srcX, int srcY, int dstX, int dstY, int width, int height,
        int srcW, int srcH, int dstW, int dstH, boolean expectEmpty,
        int expectedSrcX, int expectedSrcY, int expectedDstX, int expectedDstY, int expectedW, int expectedH) {
        final long clip = CopyRectClip.clipCopyRect(srcX, srcY, dstX, dstY, width, height, srcW, srcH, dstW, dstH);
        if (expectEmpty) {
            assertEquals(CopyRectClip.EMPTY, clip, label);
            return;
        }
        assertEquals(expectedSrcX, CopyRectClip.srcX(clip), label + " srcX");
        assertEquals(expectedSrcY, CopyRectClip.srcY(clip), label + " srcY");
        assertEquals(expectedDstX, CopyRectClip.dstX(clip, srcX, dstX), label + " dstX");
        assertEquals(expectedDstY, CopyRectClip.dstY(clip, srcY, dstY), label + " dstY");
        assertEquals(expectedW, CopyRectClip.width(clip), label + " width");
        assertEquals(expectedH, CopyRectClip.height(clip), label + " height");
    }
}
