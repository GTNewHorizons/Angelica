package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorCodeUtilsTest {

    private boolean ampersandConversionBefore;

    @BeforeEach
    void setUp() {
        ampersandConversionBefore = AngelicaConfig.enableAmpersandConversion;
        AngelicaConfig.enableAmpersandConversion = true;
        ColorCodeUtils.setConversionSuppressor(null);
    }

    @AfterEach
    void tearDown() {
        AngelicaConfig.enableAmpersandConversion = ampersandConversionBefore;
        ColorCodeUtils.setConversionSuppressor(null);
    }

    static Stream<Arguments> conversions() {
        return Stream.of(
            Arguments.of("hex shorthand", "&#ff0000Hello", "§x§f§f§0§0§0§0Hello"),
            Arguments.of("hex digit case is preserved", "&#FF0000Hi", "§x§F§F§0§0§0§0Hi"),
            Arguments.of("single codes", "&cred &lbold &rreset &qrainbow", "§cred §lbold §rreset §qrainbow"),
            Arguments.of("gradient form", "&g&#ff0000&#0000ffGrad", "§g§x§f§f§0§0§0§0§x§0§0§0§0§f§fGrad"),
            Arguments.of("bare gradient prefix stays literal", "&gplain", "&gplain"),
            Arguments.of("escaped ampersand becomes the sentinel", "\\&c", ColorCodeUtils.ESCAPED_AMPERSAND + "c"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conversions")
    void convertsAmpersandForms(String name, String input, String expected) {
        assertEquals(expected, ColorCodeUtils.convertAmpersandToSectionX(input));
    }

    @Test
    void invalidHexStaysLiteral() {
        String input = "&#zzzzzz nope";
        assertSame(input, ColorCodeUtils.convertAmpersandToSectionX(input));
    }

    @Test
    void disabledConversionReturnsSameInstance() {
        AngelicaConfig.enableAmpersandConversion = false;
        String input = "&cred";
        assertSame(input, ColorCodeUtils.convertAmpersandToSectionX(input));
    }

    @Test
    void suppressionSkipsConversionWithoutPoisoningTheCache() {
        String input = "&cred";
        ColorCodeUtils.setConversionSuppressor(() -> true);
        assertSame(input, ColorCodeUtils.convertAmpersandToSectionX(input));
        ColorCodeUtils.setConversionSuppressor(() -> false);
        assertEquals("§cred", ColorCodeUtils.convertAmpersandToSectionX(input));
    }

    @Test
    void suppressorStateIsQueryable() {
        assertFalse(ColorCodeUtils.isConversionSuppressed());
        ColorCodeUtils.setConversionSuppressor(() -> true);
        assertTrue(ColorCodeUtils.isConversionSuppressed());
    }

    @Test
    void parseSectionXRoundTrips() {
        assertEquals(0xFF0000, ColorCodeUtils.parseSectionXAt("§x§f§f§0§0§0§0", 0));
        assertEquals(-1, ColorCodeUtils.parseSectionXAt("§x§f§f§0§0", 0));
        assertEquals(-1, ColorCodeUtils.parseSectionXAt("§xzf§f§0§0§0§0", 0));
        assertTrue(ColorCodeUtils.isValidSectionX("text §x§1§2§3§4§5§6", 5));
    }
}
