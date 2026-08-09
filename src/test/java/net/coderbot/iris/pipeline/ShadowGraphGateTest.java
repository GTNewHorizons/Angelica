package net.coderbot.iris.pipeline;

import net.coderbot.iris.shadows.ShadowGraphGate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowGraphGateTest {

    @Test
    void nanAlwaysDirty() {
        assertTrue(ShadowGraphGate.shouldMarkDirty(Float.NaN, 0.25f));
    }

    @Test
    void smallDeltaClean() {
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f + 0.0004f));
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f - 0.0004f));
    }

    @Test
    void thresholdDeltaDirty() {
        assertTrue(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f + 0.001f));
        assertTrue(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f - 0.001f));
    }

    @Test
    void wrapAroundIsNotADiscontinuity() {
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.9999f, 0.0001f), "0.0002 across the wrap is below threshold");
        assertTrue(ShadowGraphGate.shouldMarkDirty(0.999f, 0.0006f), "0.0016 across the wrap is above threshold");
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.0001f, 0.9999f), "wrap works in both directions");
    }
}
