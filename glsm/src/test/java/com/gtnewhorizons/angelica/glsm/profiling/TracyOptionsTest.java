package com.gtnewhorizons.angelica.glsm.profiling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracyOptionsTest {

    @Test
    void resolveBooleanFallsBackToConfigWhenNoOverride() {
        assertTrue(TracyOptions.resolveBoolean(null, true));
        assertFalse(TracyOptions.resolveBoolean(null, false));
    }

    @Test
    void resolveBooleanOverrideWinsEitherWay() {
        assertTrue(TracyOptions.resolveBoolean(Boolean.TRUE, false));
        assertFalse(TracyOptions.resolveBoolean(Boolean.FALSE, true));
    }

    @Test
    void resolveMaxSrcLocsFallsBackToConfigWhenNoOverride() {
        assertEquals(4096, TracyOptions.resolveMaxSrcLocs(null, 4096));
    }

    @Test
    void resolveMaxSrcLocsOverrideWins() {
        assertEquals(8192, TracyOptions.resolveMaxSrcLocs(8192, 4096));
    }

    @Test
    void resolveMaxSrcLocsClampsBothConfigAndOverrideToTheMinimum() {
        assertEquals(16, TracyOptions.resolveMaxSrcLocs(null, 4));
        assertEquals(16, TracyOptions.resolveMaxSrcLocs(1, 4096));
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
