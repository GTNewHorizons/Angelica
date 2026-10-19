package net.coderbot.iris.pipeline;

import net.coderbot.iris.shadows.ShadowGraphGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowGraphGateTest {

    private static final float OVERHEAD = ShadowGraphGate.degreesToRotation(ShadowGraphGate.DEFAULT_ANGLE_DELTA_DEGREES);

    private static final float HORIZON_SCALE = ShadowGraphGate.DEFAULT_HORIZON_SCALE;

    private static float deltaForElevation(float sinElevation) {
        return ShadowGraphGate.deltaForElevation(sinElevation, OVERHEAD, HORIZON_SCALE);
    }

    @Test
    void aFullRotationIsThreeSixtyDegrees() {
        assertEquals(1.0f, ShadowGraphGate.degreesToRotation(360.0f), 1e-9f);
        assertEquals(0.25f, ShadowGraphGate.degreesToRotation(90.0f), 1e-9f);
    }

    static Stream<Arguments> dirtyCases() {
        final float horizon = deltaForElevation(0.0f);
        return Stream.of(
            Arguments.of("nan", Float.NaN, 0.25f, OVERHEAD, true),
            Arguments.of("small plus", 0.25f, 0.25f + 0.00006f, OVERHEAD, false),
            Arguments.of("small minus", 0.25f, 0.25f - 0.00006f, OVERHEAD, false),
            Arguments.of("threshold plus", 0.25f, 0.25f + 0.0002f, OVERHEAD, true),
            Arguments.of("threshold minus", 0.25f, 0.25f - 0.0002f, OVERHEAD, true),
            Arguments.of("wrap below threshold", 0.99997f, 0.00001f, OVERHEAD, false),
            Arguments.of("wrap above threshold", 0.999f, 0.0006f, OVERHEAD, true),
            Arguments.of("wrap reverse direction", 0.00001f, 0.99997f, OVERHEAD, false),
            Arguments.of("near horizon small step", 0.25f, 0.25f + 0.00006f, horizon, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dirtyCases")
    void shouldMarkDirty(String label, float last, float current, float delta, boolean expected) {
        assertEquals(expected, ShadowGraphGate.shouldMarkDirty(last, current, delta));
    }

    static Stream<Arguments> elevationDeltas() {
        final float floor = OVERHEAD * HORIZON_SCALE;
        return Stream.of(
            Arguments.of("overhead", 1.0f, OVERHEAD),
            Arguments.of("tightened", 0.8f, OVERHEAD * 0.64f),
            Arguments.of("floor at horizon", 0.0f, floor),
            Arguments.of("sin^2 0.1 is below the floor", 0.1f, floor),
            Arguments.of("sin^2 0.7 is still below the floor", 0.7f, floor),
            Arguments.of("mirror below horizon overhead", -1.0f, OVERHEAD),
            Arguments.of("mirror below horizon tightened", -0.8f, OVERHEAD * 0.64f)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("elevationDeltas")
    void deltaForElevationValues(String label, float sinElevation, float expected) {
        assertEquals(expected, deltaForElevation(sinElevation), 1e-9f);
    }

    @Test
    void toleranceTightensTowardsTheHorizon() {
        final float high = deltaForElevation(0.8f);
        assertTrue(high < deltaForElevation(1.0f));
        assertTrue(deltaForElevation(0.75f) < high);
    }
}
