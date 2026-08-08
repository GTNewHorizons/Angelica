package com.gtnewhorizons.angelica.compat.bop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BopFogBlendTest {

    private static final float EPSILON = 1e-4f;

    private static float[] reference(BopFogBlend.ColorSource source, double posX, double posZ, int playerX,
        int playerY, int playerZ, int distance) {

        float rBiomeFog = 0, gBiomeFog = 0, bBiomeFog = 0, weightBiomeFog = 0;
        for (int x = -distance; x <= distance; ++x) {
            for (int z = -distance; z <= distance; ++z) {
                final int fogColour = source.fogColour(playerX + x, playerY, playerZ + z);
                if (fogColour == BopFogBlend.NO_FOG) continue;

                float rPart = (fogColour & 0xFF0000) >> 16;
                float gPart = (fogColour & 0x00FF00) >> 8;
                float bPart = fogColour & 0x0000FF;
                float weightPart = 1;

                if (x == -distance) {
                    final double xDiff = 1 - (posX - playerX);
                    rPart *= xDiff;
                    gPart *= xDiff;
                    bPart *= xDiff;
                    weightPart *= xDiff;
                } else if (x == distance) {
                    final double xDiff = posX - playerX;
                    rPart *= xDiff;
                    gPart *= xDiff;
                    bPart *= xDiff;
                    weightPart *= xDiff;
                }

                if (z == -distance) {
                    final double zDiff = 1 - (posZ - playerZ);
                    rPart *= zDiff;
                    gPart *= zDiff;
                    bPart *= zDiff;
                    weightPart *= zDiff;
                } else if (z == distance) {
                    final double zDiff = posZ - playerZ;
                    rPart *= zDiff;
                    gPart *= zDiff;
                    bPart *= zDiff;
                    weightPart *= zDiff;
                }

                rBiomeFog += rPart;
                gBiomeFog += gPart;
                bBiomeFog += bPart;
                weightBiomeFog += weightPart;
            }
        }
        return new float[] { rBiomeFog, gBiomeFog, bBiomeFog, weightBiomeFog };
    }

    /** Deterministic pseudo-random grid: every column is either a fog biome with a stable colour, or plain. */
    private record Grid(long seed, int fogPercent) implements BopFogBlend.ColorSource {

        @Override
        public int fogColour(int x, int y, int z) {
            long h = seed;
            h = h * 31 + x;
            h = h * 31 + y;
            h = h * 31 + z;
            h ^= h >>> 27;
            h *= 0x9E3779B97F4A7C15L;
            h ^= h >>> 31;
            if (Math.floorMod(h, 100) >= fogPercent) return BopFogBlend.NO_FOG;
            return (int) (h >>> 8) & 0xFFFFFF;
        }
    }

    private final float[] out = new float[4];
    private final Object world = new Object();

    @BeforeEach
    void reset() {
        BopFogBlend.invalidate();
    }

    private void assertMatchesReference(BopFogBlend.ColorSource source, double posX, double posZ, int px, int py,
        int pz, int distance, int generation) {

        final float[] expected = reference(source, posX, posZ, px, py, pz, distance);
        BopFogBlend.accumulate(world, generation, source, posX, posZ, px, py, pz, distance, out);
        for (int i = 0; i < 4; i++) {
            final float tolerance = Math.max(EPSILON, Math.abs(expected[i]) * EPSILON);
            assertEquals(expected[i], out[i], tolerance, "component " + i);
        }
    }

    @Test
    void matchesReferenceOverRandomGridsAndPositions() {
        final Random random = new Random(12345);
        for (int iteration = 0; iteration < 200; iteration++) {
            final Grid grid = new Grid(random.nextLong(), random.nextInt(101));
            final int px = random.nextInt(4000) - 2000;
            final int py = random.nextInt(256);
            final int pz = random.nextInt(4000) - 2000;
            final double posX = px + random.nextDouble();
            final double posZ = pz + random.nextDouble();
            final int distance = random.nextInt(21);
            assertMatchesReference(grid, posX, posZ, px, py, pz, distance, iteration);
        }
    }

    @Test
    void noFogBiomesAccumulateNothing() {
        final BopFogBlend.ColorSource none = (x, y, z) -> BopFogBlend.NO_FOG;
        BopFogBlend.accumulate(world, 0, none, 10.5, 20.25, 10, 64, 20, 20, out);
        assertArrayEquals(new float[] { 0, 0, 0, 0 }, out);
    }

    @Test
    void fullCoverageMatchesReference() {
        final BopFogBlend.ColorSource all = (x, y, z) -> 0x203040;
        assertMatchesReference(all, 10.5, 20.5, 10, 64, 20, 20, 0);
    }

    @Test
    void borderOnlyCoverageMatchesReference() {
        final int distance = 12;
        final BopFogBlend.ColorSource borderOnly = (x, y, z) -> {
            final int dx = Math.abs(x - 100);
            final int dz = Math.abs(z - 200);
            return (dx == distance || dz == distance) ? 0xFF8040 : BopFogBlend.NO_FOG;
        };
        assertMatchesReference(borderOnly, 100.75, 200.125, 100, 64, 200, distance, 0);
    }

    @Test
    void interiorIsReusedAcrossSubBlockMovementAndRebuiltOnCrossing() {
        final Grid grid = new Grid(99, 60);
        for (double frac = 0.05; frac < 1.0; frac += 0.1) {
            assertMatchesReference(grid, 40 + frac, 80 + frac, 40, 64, 80, 16, 7);
        }
        assertMatchesReference(grid, 41.5, 80.5, 41, 64, 80, 16, 7);
        assertMatchesReference(grid, 41.5, 80.5, 41, 65, 80, 16, 7);
        assertMatchesReference(grid, 41.5, 80.5, 41, 65, 80, 12, 7);
    }

    @Test
    void onlyTheBorderIsWalkedWhileTheBlockPositionHolds() {
        final int distance = 20;
        final int[] lookups = { 0 };
        final BopFogBlend.ColorSource counting = (x, y, z) -> {
            lookups[0]++;
            return 0x102030;
        };

        BopFogBlend.accumulate(world, 0, counting, 100.1, 200.1, 100, 64, 200, distance, out);
        assertEquals((2 * distance + 1) * (2 * distance + 1), lookups[0], "first call must walk every column");

        lookups[0] = 0;
        BopFogBlend.accumulate(world, 0, counting, 100.9, 200.9, 100, 64, 200, distance, out);
        assertEquals(8 * distance, lookups[0], "a sub-block move must only re-walk the border strips");
    }

    @Test
    void aNewGenerationRebuildsTheInterior() {
        final int[] answer = { 0x101010 };
        final BopFogBlend.ColorSource shifting = (x, y, z) -> answer[0];

        BopFogBlend.accumulate(world, 1, shifting, 0.5, 0.5, 0, 64, 0, 8, out);
        final float firstInteriorRed = out[0];

        answer[0] = 0x202020;
        BopFogBlend.accumulate(world, 2, shifting, 0.5, 0.5, 0, 64, 0, 8, out);
        assertEquals(firstInteriorRed * 2, out[0], firstInteriorRed * EPSILON,
            "a generation bump must rebuild the cached interior, not serve the stale sum");
    }

    @Test
    void aNewWorldRebuildsTheInterior() {
        final int[] answer = { 0x101010 };
        final BopFogBlend.ColorSource shifting = (x, y, z) -> answer[0];

        BopFogBlend.accumulate(world, 0, shifting, 0.5, 0.5, 0, 64, 0, 8, out);
        final float firstInteriorRed = out[0];

        answer[0] = 0x202020;
        BopFogBlend.accumulate(new Object(), 0, shifting, 0.5, 0.5, 0, 64, 0, 8, out);
        assertEquals(firstInteriorRed * 2, out[0], firstInteriorRed * EPSILON);
    }

    @Test
    void zeroDistanceMatchesReference() {
        final BopFogBlend.ColorSource all = (x, y, z) -> 0x804020;
        assertMatchesReference(all, 5.5, 6.25, 5, 64, 6, 0, 0);
    }
}
