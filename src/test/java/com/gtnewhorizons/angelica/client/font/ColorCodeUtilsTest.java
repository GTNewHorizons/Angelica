package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorCodeUtilsTest {

    @BeforeEach
    void setUp() {
        AngelicaConfig.enableAmpersandConversion = true;
        ColorCodeUtils.setConversionSuppressor(null);
    }

    @AfterEach
    void tearDown() {
        AngelicaConfig.enableAmpersandConversion = false;
        ColorCodeUtils.setConversionSuppressor(null);
    }

    @Test
    void convertsHexShorthand() {
        assertEquals("§x§f§f§0§0§0§0Hello", ColorCodeUtils.convertAmpersandToSectionX("&#ff0000Hello"));
    }

    @Test
    void preservesHexDigitCase() {
        assertEquals("§x§F§F§0§0§0§0Hi", ColorCodeUtils.convertAmpersandToSectionX("&#FF0000Hi"));
    }

    @Test
    void convertsSingleCodes() {
        assertEquals("§cred §lbold §rreset §qrainbow", ColorCodeUtils.convertAmpersandToSectionX("&cred &lbold &rreset &qrainbow"));
    }

    @Test
    void convertsGradientForm() {
        assertEquals("§g§x§f§f§0§0§0§0§x§0§0§0§0§f§fGrad",
            ColorCodeUtils.convertAmpersandToSectionX("&g&#ff0000&#0000ffGrad"));
    }

    @Test
    void bareGradientPrefixStaysLiteral() {
        assertEquals("&gplain", ColorCodeUtils.convertAmpersandToSectionX("&gplain"));
    }

    @Test
    void invalidHexStaysLiteral() {
        String input = "&#zzzzzz nope";
        assertSame(input, ColorCodeUtils.convertAmpersandToSectionX(input));
    }

    @Test
    void escapedAmpersandBecomesSentinel() {
        assertEquals(String.valueOf(ColorCodeUtils.ESCAPED_AMPERSAND) + "c",
            ColorCodeUtils.convertAmpersandToSectionX("\\&c"));
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
