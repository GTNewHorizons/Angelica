package com.gtnewhorizons.angelica.debug.screen;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugNumbersTest {

    @ParameterizedTest(name = "{0} at {1} -> {2}")
    @CsvSource({
        "0.5, 0, 1", "2.5, 0, 3", "-2.5, 0, -3", "0.125, 2, 0.13", "-0.125, 2, -0.13", "0.99999, 2, 1.00",
        "-0.0004, 3, 0.000", "-0.0, 3, 0.000", "-0.4, 0, 0",
        "7, 3, 7.000", "0.00001, 5, 0.00001", "-12.5, 2, -12.50",
        "30000000.12345, 5, 30000000.12345", "-30000000.5, 5, -30000000.50000",
        "1.2, -3, 1", "1.2, 9, 1.200000",
        "NaN, 2, NaN", "Infinity, 2, Infinity", "-Infinity, 2, -Infinity" })
    void appendFixed(double value, int decimals, String expected) {
        final StringBuilder out = new StringBuilder();
        DebugNumbers.appendFixed(out, value, decimals);
        assertEquals(expected, out.toString());
    }

    @ParameterizedTest(name = "{0} width {1} -> {2}")
    @CsvSource({ "5, 3, 005", "0, 3, 000", "0, 1, 0", "1234, 2, 1234", "12, 0, 12" })
    void appendPadded(long value, int width, String expected) {
        final StringBuilder out = new StringBuilder();
        DebugNumbers.appendPadded(out, value, width);
        assertEquals(expected, out.toString());
    }
}
