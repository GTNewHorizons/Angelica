package com.gtnewhorizons.angelica.debug.screen.renderers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterLevelRendererTest {

    @ParameterizedTest
    @CsvSource({"0,8", "1,7", "7,1", "8,8"})
    void level(int meta, int expected) {
        assertEquals(expected, WaterLevelRenderer.level(meta));
    }
}
