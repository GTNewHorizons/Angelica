package com.gtnewhorizons.angelica.compat.debug;

import com.gtnewhorizons.angelica.debug.screen.RecordingDisplayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotaniaDebugCompatTest {

    private static RecordingDisplayer write() {
        final RecordingDisplayer displayer = new RecordingDisplayer();
        BotaniaDebugEntry.writeFacts(displayer, RecordingDisplayer.GROUP, 12, 3, 40, 5, 2, 7, 9, "1.13.32-GTNH");
        return displayer;
    }

    @Test
    void writesFiveFactsFromTheCounters() {
        final RecordingDisplayer displayer = write();
        assertEquals("[Sparkles, Wisps, Lightning, Mana Network, Version]", displayer.names.toString());
        assertEquals("12 (3 fake)", displayer.text(0));
        assertEquals("40 (5 depth-ignoring)", displayer.text(1));
        assertEquals("2", displayer.text(2));
        assertEquals("7 collectors, 9 pools", displayer.text(3));
        assertEquals("1.13.32-GTNH", displayer.text(4));
    }

    @Test
    void contextBlockMatchesTheLegacyConfigAndGlLines() {
        final RecordingDisplayer displayer = new RecordingDisplayer();
        BotaniaDebugEntry.writeContext(displayer, RecordingDisplayer.GROUP, true, 7, "3.3.0", "Test GPU", "3.30", 16, true, false, true);
        assertEquals("[null, shaders.enabled, shaders.secondaryUnit, null, GL_VERSION, GL_RENDERER, GL_SHADING_LANGUAGE_VERSION, GL_MAX_TEXTURE_IMAGE_UNITS_ARB, GL_ARB_multitexture, GL_ARB_texture_non_power_of_two, OpenGL13]", displayer.names.toString());
        assertEquals("[Config Context, true, 7, OpenGL Context, 3.3.0, Test GPU, 3.30, 16, true, false, true]", displayer.texts.toString());
    }

    @ParameterizedTest
    @CsvSource(value = { "1.13.32-GTNH,1.13.32-GTNH", "GRADLETOKEN_VERSION,N/A", "NIL,N/A" }, nullValues = "NIL")
    void displayVersionIsNotAvailableForTokensAndMissingValues(String version, String expected) {
        assertEquals(expected, BotaniaDebugEntry.displayVersion(version));
    }
}
