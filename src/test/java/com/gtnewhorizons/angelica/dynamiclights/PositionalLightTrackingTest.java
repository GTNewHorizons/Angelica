package com.gtnewhorizons.angelica.dynamiclights;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A positional light has to rebuild every chunk section its light shows up in, and the ones it leaves. */
class PositionalLightTrackingTest {

    private static final float[] REACHES = { -1, 0.4f, 1, 1.875f, 5, 7, 7.5f, 8, 11, 15 };
    private static final int[] LUMINANCES = { 1, 6, 11, 15 };
    // Section middles, edges and corners, and either side of the middle where a torch reach just gets out both ways
    private static final double[] OFFSETS = { 0, 0.05, 0.6, 3, 7.3, 8, 8.7, 13, 15.4, 15.95 };

    private boolean frustumCulling;

    @BeforeEach
    void rebuildStraightAway() {
        // Otherwise rebuilds queue up in the rebuild manager until a frame runs
        frustumCulling = DynamicLights.FrustumCullingEnabled;
        DynamicLights.FrustumCullingEnabled = false;
    }

    @AfterEach
    void restore() {
        DynamicLights.FrustumCullingEnabled = frustumCulling;
    }

    @Test
    void rebuildsEverySectionTheLightGetsInto() {
        for (float reach : REACHES) {
            for (int luminance : LUMINANCES) {
                for (double ox : OFFSETS) {
                    for (double oy : OFFSETS) {
                        for (double oz : OFFSETS) {
                            check(reach, luminance, -32 + ox, 64 + oy, 160 + oz);
                        }
                    }
                }
            }
        }
    }

    private static void check(float reach, int luminance, double x, double y, double z) {
        final PositionalLightSource light = new PositionalLightSource();
        light.setReach(reach);
        set(light, "luminance", luminance);
        set(light, "x", x);
        set(light, "y", y);
        set(light, "z", z);
        final Recorder rebuilt = new Recorder();
        assertTrue(light.angelica$updateDynamicLight(rebuilt));

        final String what = "reach " + reach + " luminance " + luminance + " at " + x + " " + y + " " + z;
        final int cx = (int) Math.floor(x) >> 4, cy = (int) Math.floor(y) >> 4, cz = (int) Math.floor(z) >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    final boolean tracked = rebuilt.sections.contains(CoordinatePacker.pack(cx + dx, cy + dy, cz + dz));
                    if (tracked) {
                        assertTrue(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) <= 1, what);
                    } else {
                        assertFalse(lights(light, cx + dx, cy + dy, cz + dz),
                            what + " misses section " + dx + " " + dy + " " + dz);
                    }
                }
            }
        }
        if (reach >= 0 && reach <= 7) assertTrue(rebuilt.sections.size() <= 8, what);
    }

    // What the mesher reads for a section: the light in the middle of each of its cells and the cells a block outside
    // it (smooth lighting reads those for faces on the edge)
    private static boolean lights(PositionalLightSource light, int sx, int sy, int sz) {
        for (int x = -1; x <= 16; x++) {
            for (int y = -1; y <= 16; y++) {
                for (int z = -1; z <= 16; z++) {
                    if (DynamicLights.maxDynamicLightLevel((sx << 4) + x, (sy << 4) + y, (sz << 4) + z, light, 0) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void rebuildsWhatItLeaves() {
        final PositionalLightSource light = new PositionalLightSource();
        light.setReach(5);
        set(light, "luminance", 15);
        set(light, "x", 8.0);
        set(light, "y", 8.0);
        set(light, "z", 8.0);
        final Recorder first = new Recorder();
        light.angelica$updateDynamicLight(first);
        assertEquals(1, first.sections.size());

        // Three sections along, nothing in common with where it was
        set(light, "x", 56.0);
        final Recorder second = new Recorder();
        assertTrue(light.angelica$updateDynamicLight(second));
        assertTrue(second.sections.contains(CoordinatePacker.pack(0, 0, 0)), "the section it left");
        assertTrue(second.sections.contains(CoordinatePacker.pack(3, 0, 0)), "the section it went to");
        assertEquals(2, second.sections.size());
    }

    @Test
    void sittingStillRebuildsNothing() {
        final PositionalLightSource light = new PositionalLightSource();
        set(light, "luminance", 15);
        set(light, "x", 8.0);
        set(light, "y", 8.0);
        set(light, "z", 8.0);
        final Recorder rebuilt = new Recorder();
        assertTrue(light.angelica$updateDynamicLight(rebuilt));
        rebuilt.sections.clear();
        assertFalse(light.angelica$updateDynamicLight(rebuilt));
        assertTrue(rebuilt.sections.isEmpty());

        // A new reach counts as a change
        light.setReach(15);
        assertTrue(light.angelica$updateDynamicLight(rebuilt));
        assertEquals(27, rebuilt.sections.size());

        // A tiny one doesn't, like a tiny move
        rebuilt.sections.clear();
        light.setReach(14.95f);
        assertFalse(light.angelica$updateDynamicLight(rebuilt));
        assertTrue(rebuilt.sections.isEmpty());
    }

    private static final class Recorder implements IDynamicLightWorldRenderer {

        final LongOpenHashSet sections = new LongOpenHashSet();

        @Override
        public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
            sections.add(CoordinatePacker.pack(x, y, z));
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    // The setters add the light to Angelica's lights, which needs a game. Tracking only reads the fields
    private static void set(PositionalLightSource light, String field, Object value) {
        try {
            final Field f = PositionalLightSource.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(light, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
