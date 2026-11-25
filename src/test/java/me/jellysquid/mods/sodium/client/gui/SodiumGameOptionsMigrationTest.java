package me.jellysquid.mods.sodium.client.gui;

import me.jellysquid.mods.sodium.client.gui.options.named.TranslucencyMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SodiumGameOptionsMigrationTest {

    @ParameterizedTest(name = "{0} -> {2}")
    @CsvSource(delimiter = '|', value = {
        "legacy on      | \"translucency_sorting\": true   | FAST",
        "legacy off     | \"translucency_sorting\": false  | OFF",
        "mode stored    | \"translucency_mode\": \"FANCY\" | FANCY",
        "unknown mode   | \"translucency_mode\": \"BOGUS\" | FAST",
    })
    void migratesTranslucencySetting(String name, String stored, TranslucencyMode expected) {
        final SodiumGameOptions options = SodiumGameOptions.read(new StringReader("{ \"performance\": { " + stored + " } }"));

        assertEquals(expected, options.performance.translucencyMode);
        assertFalse(options.toJson().contains("translucency_sorting"), "the legacy key is dropped on save");
    }
}
