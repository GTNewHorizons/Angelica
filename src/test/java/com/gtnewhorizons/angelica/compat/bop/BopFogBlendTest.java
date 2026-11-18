package com.gtnewhorizons.angelica.compat.bop;

import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.gtnewhorizons.angelica.compat.bop.BopFogTestSupport.Grid;

import static com.gtnewhorizons.angelica.compat.bop.BopFogTestSupport.mockWorld;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BopFogBlendTest {

    private record Case(String name, long seed, int fogPercent, double posX, double posZ, int playerX, int playerY,
        int playerZ, int distance, int expectedR, int expectedG, int expectedB, int expectedWeight) {
    }

    private static final List<Case> CASES = List.of(
        new Case("distance0", 11L, 100, 10.5, 20.25, 10, 64, 20, 0, 0x429fc000, 0x41ed0000, 0x42a98000, 0x3ec00000),
        new Case("distance1", 12L, 85, 5.25, 8.75, 5, 70, 8, 1, 0x43adb800, 0x43b41000, 0x43304000, 0x40100000),
        new Case("distance2", 13L, 65, -3.5, 12.125, -4, 64, 12, 2, 0x44b2f200, 0x44b3de00, 0x44b93800, 0x412a0000),
        new Case("distance6", 14L, 50, 100.75, -200.25, 100, 80, -200, 6, 0x46026100, 0x45fb6880, 0x45e48f80, 0x42748000),
        new Case("distance15", 15L, 35, -500.5, 500.5, -500, 64, 500, 15, 0x47166700, 0x4716bb40, 0x470f8c40, 0x4397c000),
        new Case("distance34", 16L, 20, 1000.25, -1000.75, 1000, 64, -1000, 34, 0x47e8dd20, 0x47e14200, 0x47e63760, 0x44656000),
        new Case("noFogGrid", 17L, 0, 42.5, -17.25, 42, 64, -17, 10, 0x0, 0x0, 0x0, 0x0),
        new Case("borderOnlyGrid", 18L, 5, 100.75, 200.125, 100, 64, 200, 12, 0x457fe200, 0x4585bb00, 0x45a5c600, 0x42150000));

    private final float[] actual = new float[4];

    @BeforeEach
    void reset() {
        BopFogBlend.invalidate();
        FogBiomeCache.invalidate();
    }

    private static void assertBitEqual(float[] expected, float[] actual) {
        assertEquals(Float.floatToRawIntBits(expected[0]), Float.floatToRawIntBits(actual[0]), "r");
        assertEquals(Float.floatToRawIntBits(expected[1]), Float.floatToRawIntBits(actual[1]), "g");
        assertEquals(Float.floatToRawIntBits(expected[2]), Float.floatToRawIntBits(actual[2]), "b");
        assertEquals(Float.floatToRawIntBits(expected[3]), Float.floatToRawIntBits(actual[3]), "weight");
    }

    @Test
    void productionMatchesGoldenBits() {
        for (Case c : CASES) {
            final Grid grid = new Grid(c.seed(), c.fogPercent());
            final World world = mockWorld(grid);
            BopFogBlend.invalidate();
            BopFogBlend.accumulate(world, c.posX(), c.posZ(), c.playerX(), c.playerY(), c.playerZ(), c.distance(), actual);
            assertEquals(c.expectedR(), Float.floatToRawIntBits(actual[0]), c.name() + " r");
            assertEquals(c.expectedG(), Float.floatToRawIntBits(actual[1]), c.name() + " g");
            assertEquals(c.expectedB(), Float.floatToRawIntBits(actual[2]), c.name() + " b");
            assertEquals(c.expectedWeight(), Float.floatToRawIntBits(actual[3]), c.name() + " weight");
        }
    }

    @Test
    void noFogBiomesAccumulateNothing() {
        final Grid grid = new Grid(1, 0);
        final World world = mockWorld(grid);
        BopFogBlend.accumulate(world, 10.5, 20.25, 10, 64, 20, 20, actual);
        assertBitEqual(new float[] { 0, 0, 0, 0 }, actual);
    }
}
