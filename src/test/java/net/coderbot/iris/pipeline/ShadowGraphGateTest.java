package net.coderbot.iris.pipeline;

import net.coderbot.iris.shadows.ShadowGraphGate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowGraphGateTest {

    private static final float OVERHEAD = ShadowGraphGate.degreesToRotation(ShadowGraphGate.DEFAULT_ANGLE_DELTA_DEGREES);

    private static final float HORIZON_SCALE = ShadowGraphGate.DEFAULT_HORIZON_SCALE;

    private static float deltaForElevation(float sinElevation) {
        return ShadowGraphGate.deltaForElevation(sinElevation, OVERHEAD, HORIZON_SCALE);
    }

    @Test
    void nanAlwaysDirty() {
        assertTrue(ShadowGraphGate.shouldMarkDirty(Float.NaN, 0.25f, OVERHEAD));
    }

    @Test
    void smallDeltaClean() {
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f + 0.00006f, OVERHEAD));
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f - 0.00006f, OVERHEAD));
    }

    @Test
    void thresholdDeltaDirty() {
        assertTrue(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f + 0.0002f, OVERHEAD));
        assertTrue(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f - 0.0002f, OVERHEAD));
    }

    @Test
    void aFullRotationIsThreeSixtyDegrees() {
        assertEquals(1.0f, ShadowGraphGate.degreesToRotation(360.0f), 1e-9f);
        assertEquals(0.25f, ShadowGraphGate.degreesToRotation(90.0f), 1e-9f);
    }

    @Test
    void wrapAroundIsNotADiscontinuity() {
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.99997f, 0.00001f, OVERHEAD), "0.00004 across the wrap is below threshold");
        assertTrue(ShadowGraphGate.shouldMarkDirty(0.999f, 0.0006f, OVERHEAD), "0.0016 across the wrap is above threshold");
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.00001f, 0.99997f, OVERHEAD), "wrap works in both directions");
    }

    @Test
    void overheadLightUsesTheFullTolerance() {
        assertEquals(OVERHEAD, deltaForElevation(1.0f), 1e-9f);
    }

    @Test
    void toleranceTightensTowardsTheHorizon() {
        // The default floor is 0.5, so tightening only bites above sin^2 0.5
        final float high = deltaForElevation(0.8f);
        assertEquals(OVERHEAD * 0.64f, high, 1e-9f);
        assertTrue(high < deltaForElevation(1.0f));
        assertTrue(deltaForElevation(0.75f) < high);
    }

    @Test
    void toleranceIsFlooredAtTheHorizon() {
        final float floor = OVERHEAD * HORIZON_SCALE;
        assertEquals(floor, deltaForElevation(0.0f), 1e-9f);
        assertEquals(floor, deltaForElevation(0.1f), 1e-9f, "sin^2 0.1 is below the floor");
        assertEquals(floor, deltaForElevation(0.7f), 1e-9f, "sin^2 0.7 is still below the floor");
    }

    @Test
    void elevationBelowTheHorizonMirrorsAbove() {
        // The shadow light points down at night; sin^2 keeps the sign out of it
        assertEquals(deltaForElevation(1.0f), deltaForElevation(-1.0f), 1e-9f);
        assertEquals(deltaForElevation(0.8f), deltaForElevation(-0.8f), 1e-9f);
    }

    @Test
    void nearTheHorizonASmallStepIsDirty() {
        final float horizon = deltaForElevation(0.0f);
        assertFalse(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f + 0.00006f, OVERHEAD));
        assertTrue(ShadowGraphGate.shouldMarkDirty(0.25f, 0.25f + 0.00006f, horizon),
            "0.00006 clears the tightened horizon threshold even though it is clean overhead");
    }
}
