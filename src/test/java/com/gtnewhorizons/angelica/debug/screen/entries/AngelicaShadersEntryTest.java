package com.gtnewhorizons.angelica.debug.screen.entries;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AngelicaShadersEntryTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
        "[AngelicaShaders] Shadow Maps: not used|Shadow Maps: not used",
        "[AngelicaShaders - Shadow Pass]|''",
        "  Shadow Entities: 3|Shadow Entities: 3",
        "Profile: HIGH|Profile: HIGH",
        "NULL|''"})
    void stripTag(String line, String expected) {
        assertEquals(expected, AngelicaShadersEntry.stripTag(line));
    }
}
