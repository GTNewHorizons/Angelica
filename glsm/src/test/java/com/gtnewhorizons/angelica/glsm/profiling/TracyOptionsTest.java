package com.gtnewhorizons.angelica.glsm.profiling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracyOptionsTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(value = {
        "no override falls back to config true, null, true, true",
        "no override falls back to config false, null, false, false",
        "override true wins, true, false, true",
        "override false wins, false, true, false"
    }, nullValues = "null")
    void resolveBoolean(String name, Boolean override, boolean config, boolean expected) {
        assertEquals(expected, TracyOptions.resolveBoolean(override, config));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(value = {
        "no override falls back to config, null, 4096, 4096",
        "override wins, 8192, 4096, 8192",
        "config is clamped to the minimum, null, 4, 16",
        "override is clamped to the minimum, 1, 4096, 16"
    }, nullValues = "null")
    void resolveMaxSrcLocs(String name, Integer override, int config, int expected) {
        assertEquals(expected, TracyOptions.resolveMaxSrcLocs(override, config));
    }

    @Test
    void latchExposesConfigValuesThroughTheAccessors() {
        TracyOptions.latch(true, true, true, 8192);
        assertTrue(TracyOptions.configEnabled());
        assertTrue(TracyOptions.configRemote());
        assertTrue(TracyOptions.configFineZones());
        assertEquals(8192, TracyOptions.configMaxSrcLocs());

        TracyOptions.latch(false, false, false, 4096);
        assertFalse(TracyOptions.configEnabled());
        assertFalse(TracyOptions.configRemote());
        assertFalse(TracyOptions.configFineZones());
        assertEquals(4096, TracyOptions.configMaxSrcLocs());
    }

    @Test
    void allowRemoteHasNoJvmOverrideAndTracksConfigDirectly() {
        TracyOptions.latch(false, true, false, 4096);
        assertTrue(TracyOptions.allowRemote());

        TracyOptions.latch(false, false, false, 4096);
        assertFalse(TracyOptions.allowRemote());
    }
}
