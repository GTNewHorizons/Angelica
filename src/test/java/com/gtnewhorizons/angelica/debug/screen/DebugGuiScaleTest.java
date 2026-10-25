package com.gtnewhorizons.angelica.debug.screen;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugGuiScaleTest {

    @ParameterizedTest(name = "{0}x{1} even={2}")
    @CsvSource({ "854, 480, false, 2", "854, 480, true, 2", "1280, 720, false, 3", "1280, 720, true, 2", "1920, 1080, false, 4", "2560, 1440, false, 6", "3840, 2160, false, 9", "3840, 2160, true, 8", "100, 100, false, 1" })
    void maxScale(int width, int height, boolean even, int expected) {
        assertEquals(expected, DebugGuiScale.maxScale(width, height, even));
    }

    @ParameterizedTest(name = "setting={0} game={1} {2}x{3}")
    @CsvSource({
        DebugGuiScale.UNCHANGED + ", 7, 3840, 2160, 7", DebugGuiScale.AUTO + ", 9, 3840, 2160, 4", DebugGuiScale.AUTO + ", 2, 854, 480, 1",
        DebugGuiScale.AUTO + ", 3, 3840, 2160, 3", DebugGuiScale.AUTO + ", 1, 100, 100, 1",
        "3, 4, 1920, 1080, 3", "8, 4, 1920, 1080, 4", "8, 9, 854, 480, 2", "4, 2, 1920, 1080, 2", "1, 1, 854, 480, 1" })
    void resolveCapsAtTheDisplayMaxAndTheGameScale(int setting, int gameScale, int width, int height, int expected) {
        assertEquals(expected, DebugGuiScale.resolve(setting, gameScale, width, height, false));
    }

    @ParameterizedTest(name = "max={0} game={1}")
    @CsvSource({ "4, 2, 2", "4, 4, 4", "4, 9, 4", "1, 3, 1", "4, 0, 1" })
    void offeredMaxIsCappedByGameScaleAndResolvesToItself(int max, int gameScale, int expected) {
        assertEquals(expected, DebugGuiScale.offeredMax(max, gameScale));
        if (gameScale > 0) assertEquals(expected, DebugGuiScale.resolve(expected, gameScale, 3840, 2160, false));
    }
}
