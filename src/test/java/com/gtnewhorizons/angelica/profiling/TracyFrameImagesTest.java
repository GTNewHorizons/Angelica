package com.gtnewhorizons.angelica.profiling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TracyFrameImagesTest {

    @ParameterizedTest
    @CsvSource({
        "1920, 1080, 320, 180",
        "1920, 400, 320, 64",
        "800, 1200, 120, 180",
        "300, 170, 300, 168",
        "2, 2, 4, 4",
    })
    void captureSizeFitsInsideTheTracyBoxKeepingAspect(int srcWidth, int srcHeight, int width, int height) {
        final long size = TracyFrameImages.fitCaptureSize(srcWidth, srcHeight);
        assertEquals(width, (int) (size >>> 32), "width for " + srcWidth + "x" + srcHeight);
        assertEquals(height, (int) size, "height for " + srcWidth + "x" + srcHeight);
    }
}
