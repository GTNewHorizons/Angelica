package com.gtnewhorizons.angelica.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GLVersionChoiceTest {

    @ParameterizedTest(name = "{0}: of({1}, {2}) -> {3}")
    @CsvSource({
        "zero is auto, 0, 46, AUTO",
        "below the floor is auto, 32, 46, AUTO",
        "nonexistent version rounds down, 34, 46, GL33",
        "nonexistent version rounds down, 39, 46, GL33",
        "existing version below the ceiling, 45, 46, GL45",
        "ceiling caps, 46, 41, GL41",
        "ceiling caps, 41, 33, GL33",
    })
    void resolvesRequestedVersion(String name, int requested, int ceiling, GLVersionChoice expected) {
        assertEquals(expected, GLVersionChoice.of(requested, ceiling));
    }
}
