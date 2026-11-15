package com.gtnewhorizons.angelica.dynamiclights;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicLightsFalloffTest {

    private static final double EPS = 1e-9;

    @Test
    void reachFifteenIsVanillaBlockLight() {
        // A real block of luminance L lights L - distance
        for (int luminance = 1; luminance <= 15; luminance++) {
            for (int d = 0; d <= 16; d++) {
                assertEquals(Math.max(0, luminance - d), DynamicLights.falloff(luminance, 15, d), EPS);
            }
        }
    }

    @Test
    void shorterReachFadesSooner() {
        assertEquals(15, DynamicLights.falloff(15, 5, 0), EPS);
        assertEquals(6, DynamicLights.falloff(15, 5, 3), EPS);
        assertEquals(0, DynamicLights.falloff(15, 5, 5), EPS);
        assertEquals(0, DynamicLights.falloff(15, 5, 6), EPS);
    }

    @Test
    void torchReachIsTheOldFalloff() {
        assertEquals(15, DynamicLights.falloff(15, -1, 0), EPS);
        assertEquals((1 - 4 / 7.75) * 15, DynamicLights.falloff(15, -1, 4), EPS);
        assertEquals(0, DynamicLights.falloff(15, -1, 7.8), EPS);
        assertEquals(DynamicLights.falloff(12, -1, 3), DynamicLights.falloff(12, 3), EPS);
    }

    @Test
    void zeroReachOrLuminanceGivesNothing() {
        assertEquals(0, DynamicLights.falloff(15, 0, 0), EPS);
        assertEquals(0, DynamicLights.falloff(0, 15, 0), EPS);
    }

    @Test
    void fractionalReach() {
        // An eighth size glowstone: 15 / 1.875 = 8 levels a block
        assertEquals(15, DynamicLights.falloff(15, 1.875f, 0), EPS);
        assertEquals(7, DynamicLights.falloff(15, 1.875f, 1), EPS);
        assertEquals(0, DynamicLights.falloff(15, 1.875f, 1.875), EPS);
        assertEquals(0, DynamicLights.falloff(15, 1.875f, 2), EPS);
    }

    @Test
    void reachOfFollowsLuminance() {
        final PositionalLightSource light = new PositionalLightSource();
        assertEquals(0, DynamicLights.reachOf(light), EPS);
        light.setReach(15);
        setLuminanceWithoutTracking(light, 15);
        assertEquals(15, DynamicLights.reachOf(light), EPS);
        setLuminanceWithoutTracking(light, 6);
        assertEquals(6, DynamicLights.reachOf(light), EPS);
        light.setReach(-1);
        assertEquals(7.75, DynamicLights.reachOf(light), EPS);
        light.setReach(99);
        assertEquals(15, light.getReach(), EPS);
        light.setReach(1.875f);
        assertEquals(0.75, DynamicLights.reachOf(light), EPS);
        light.setReach(-0.5f);
        assertEquals(-1, light.getReach(), EPS);
    }

    // setLuminance adds the light to Angelica's lights, which needs a game. The field is all reachOf reads
    private static void setLuminanceWithoutTracking(PositionalLightSource light, int luminance) {
        try {
            final java.lang.reflect.Field f = PositionalLightSource.class.getDeclaredField("luminance");
            f.setAccessible(true);
            f.setInt(light, luminance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
