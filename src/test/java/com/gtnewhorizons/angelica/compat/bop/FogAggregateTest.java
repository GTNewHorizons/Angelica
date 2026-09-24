package com.gtnewhorizons.angelica.compat.bop;

import com.gtnewhorizons.angelica.compat.bop.BopFogTestSupport.BopFog;
import com.gtnewhorizons.angelica.compat.bop.BopFogTestSupport.Grid;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.gtnewhorizons.angelica.compat.bop.BopFogTestSupport.mockWorld;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class FogAggregateTest {

    @BeforeEach
    void reset() {
        FogBiomeCache.invalidate();
    }

    private static World world(AtomicBoolean loaded, AtomicInteger color) {
        final World world = mock(World.class);
        final IChunkProvider provider = mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(provider);
        when(provider.chunkExists(anyInt(), anyInt())).thenAnswer(inv -> loaded.get());
        final BiomeGenBase biome = BopFog.mockBiome();
        when(world.getBiomeGenForCoords(anyInt(), anyInt())).thenReturn(biome);
        when(BopFog.stubCall(biome)).thenAnswer(inv -> color.get());
        return world;
    }

    private static void assertBits(float[] expected, float[] actual) {
        for (int i = 0; i < 4; i++) {
            assertEquals(Float.floatToRawIntBits(expected[i]), Float.floatToRawIntBits(actual[i]), "component " + i);
        }
    }

    @Test
    void fractionalMotionMatchesFreshAccumulation() {
        final World world = mockWorld(new Grid(923L, 65));
        final float[] actual = new float[4];
        final float[] expected = new float[4];
        for (double fraction : new double[] {0, .125, .5, .875}) {
            BopFogBlend.accumulate(world, 10.25, -20.25, 10, 72, -21, 6, actual);
            BopFogBlend.accumulate(world, 10 + fraction, -21 + fraction, 10, 72, -21, 6, actual);
            BopFogBlend.invalidate();
            BopFogBlend.accumulate(world, 10 + fraction, -21 + fraction, 10, 72, -21, 6, expected);
            assertBits(expected, actual);
        }
    }

    @Test
    void integerCoordinatesHeightAndDistanceSelectTheirOwnTotals() {
        final World world = mockWorld(new Grid(729L, 83));
        final float[] actual = new float[4];
        final float[] expected = new float[4];
        final int[][] keys = {{10,64,20,2},{11,64,20,2},{11,65,20,2},{11,65,21,2},{11,65,21,3},{10,64,20,2}};
        for (int[] key : keys) {
            FogColourGrid.accumulateInterior(world, key[0], key[1], key[2], key[3], actual);
            FogColourGrid.invalidate();
            FogColourGrid.accumulateInterior(world, key[0], key[1], key[2], key[3], expected);
            assertBits(expected, actual);
        }
    }

    @Test
    void colorEvictionDoesNotDiscardInteriorTotals() {
        final AtomicInteger color = new AtomicInteger(0x010203);
        final World world = world(new AtomicBoolean(true), color);
        final float[] actual = new float[4];
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        color.set(0x040506);
        FogColourGrid.colour(world, 128, 64, 0);
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        assertBits(new float[] {1,2,3,1}, actual);
    }

    @Test
    void biomeEvictionDoesNotDiscardInteriorTotals() {
        final AtomicInteger color = new AtomicInteger(0x010203);
        final World world = world(new AtomicBoolean(true), color);
        final float[] actual = new float[4];
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        color.set(0x040506);
        FogColourGrid.colour(world, 128, 64, 0);
        for (int x = 1; x <= 16385; x++) FogBiomeCache.get(world, x, 1);
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        assertBits(new float[] {1,2,3,1}, actual);
    }

    @Test
    void bothInvalidationEntryPointsClearBothCacheLevels() {
        final AtomicInteger color = new AtomicInteger(0x010203);
        final World world = world(new AtomicBoolean(true), color);
        final float[] actual = new float[4];
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        color.set(0x040506);
        FogBiomeCache.invalidate();
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        assertBits(new float[] {4,5,6,1}, actual);
        color.set(0x070809);
        BopFogBlend.invalidate();
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        assertBits(new float[] {7,8,9,1}, actual);
    }

    @Test
    void worldReplacementAlsoInvalidatesAfterBiomeOnlyLookups() {
        final World first = world(new AtomicBoolean(true), new AtomicInteger(0x010203));
        final World second = world(new AtomicBoolean(true), new AtomicInteger(0x040506));
        final float[] actual = new float[4];
        FogColourGrid.accumulateInterior(first, 0, 64, 0, 1, actual);
        FogBiomeCache.get(second, 0, 0);
        FogColourGrid.accumulateInterior(second, 0, 64, 0, 1, actual);
        assertBits(new float[] {4,5,6,1}, actual);
        FogColourGrid.accumulateInterior(first, 0, 64, 0, 1, actual);
        assertBits(new float[] {1,2,3,1}, actual);
    }

    @Test
    void unloadedInteriorRetriesUntilLoadedThenReuses() {
        final AtomicBoolean loaded = new AtomicBoolean(false);
        final AtomicInteger color = new AtomicInteger(0x010203);
        final World world = world(loaded, color);
        final float[] actual = new float[4];
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        color.set(0x040506);
        loaded.set(true);
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        assertBits(new float[] {4,5,6,1}, actual);
        color.set(0x070809);
        FogColourGrid.colour(world, 128, 64, 0);
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        assertBits(new float[] {4,5,6,1}, actual);
    }

    @Test
    void unloadedNoFogInteriorDoesNotBecomePermanentlyEmpty() {
        final AtomicBoolean loaded = new AtomicBoolean(false);
        final World world = world(loaded, new AtomicInteger(0x010203));
        final BiomeGenBase fog = world.getBiomeGenForCoords(0, 0);
        when(world.getBiomeGenForCoords(anyInt(), anyInt())).thenReturn(mock(BiomeGenBase.class));
        final float[] actual = new float[4];
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        assertBits(new float[4], actual);
        loaded.set(true);
        when(world.getBiomeGenForCoords(anyInt(), anyInt())).thenReturn(fog);
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        assertBits(new float[] {1,2,3,1}, actual);
    }

    @Test
    void unloadedPerimeterDoesNotInvalidateReusableInterior() {
        final AtomicInteger color = new AtomicInteger(0x010203);
        final World world = world(new AtomicBoolean(true), color);
        when(world.getChunkProvider().chunkExists(anyInt(), anyInt()))
            .thenAnswer(inv -> (int) inv.getArgument(0) != 1 && (int) inv.getArgument(1) != 1);
        final float[] actual = new float[4];
        BopFogBlend.accumulate(world, 15.5, 15.5, 15, 64, 15, 1, actual);
        color.set(0x040506);
        FogColourGrid.colour(world, 143, 64, 15);
        FogColourGrid.accumulateInterior(world, 15, 64, 15, 1, actual);
        assertBits(new float[] {1,2,3,1}, actual);
        BopFogBlend.accumulate(world, 15.75, 15.75, 15, 64, 15, 1, actual);
        FogColourGrid.accumulateInterior(world, 15, 64, 15, 1, actual);
        assertBits(new float[] {1,2,3,1}, actual);
    }

    @Test
    void cachedGridHitIsCacheableAfterAnUnloadedLookup() {
        final World world = world(new AtomicBoolean(true), new AtomicInteger(0x010203));
        when(world.getChunkProvider().chunkExists(anyInt(), anyInt()))
            .thenAnswer(inv -> (int) inv.getArgument(0) != 1);
        final BiomeGenBase biome = world.getBiomeGenForCoords(0, 0);
        FogColourGrid.colour(world, 0, 64, 0);
        FogColourGrid.colour(world, 16, 64, 0);
        final float[] actual = new float[4];
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        FogColourGrid.colour(world, 128, 64, 0);
        FogColourGrid.accumulateInterior(world, 0, 64, 0, 1, actual);
        BopFog.verifyCall(biome, times(3));
    }
}
