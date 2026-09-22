package com.gtnewhorizons.angelica.compat.bop;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.IntBinaryOperator;

import com.gtnewhorizons.angelica.compat.bop.BopFogTestSupport.BopFog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class FogColourGridTest {

    @BeforeEach
    void reset() {
        FogColourGrid.invalidate();
        FogBiomeCache.invalidate();
    }

    private static World mockWorld(boolean chunksLoaded) {
        final World world = Mockito.mock(World.class);
        final IChunkProvider provider = Mockito.mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(provider);
        when(provider.chunkExists(anyInt(), anyInt())).thenReturn(chunksLoaded);
        return world;
    }

    private static BiomeGenBase fixedFogBiome(World world, int color) {
        final BiomeGenBase biome = BopFog.mockBiome();
        when(world.getBiomeGenForCoords(anyInt(), anyInt())).thenReturn(biome);
        when(BopFog.stubCall(biome)).thenReturn(color);
        return biome;
    }

    private static BiomeGenBase dynamicFogBiome(World world, IntBinaryOperator colorForXZ) {
        final BiomeGenBase biome = BopFog.mockBiome();
        when(world.getBiomeGenForCoords(anyInt(), anyInt())).thenReturn(biome);
        when(BopFog.stubCall(biome)).thenAnswer(inv -> colorForXZ.applyAsInt(inv.getArgument(0), inv.getArgument(2)));
        return biome;
    }

    @Test
    void noAliasingAcrossA69WideWindowIncludingNegativeCoords() {
        final World world = mockWorld(true);
        dynamicFogBiome(world, (x, z) -> ((x & 0xFFF) << 12 | (z & 0xFFF)) & 0xFFFFFF);

        final int centerX = -40;
        final int centerZ = -40;
        final int distance = 34;

        for (int x = centerX - distance; x <= centerX + distance; x++) {
            for (int z = centerZ - distance; z <= centerZ + distance; z++) {
                final int expected = ((x & 0xFFF) << 12 | (z & 0xFFF)) & 0xFFFFFF;
                final int actual = FogColourGrid.colour(world, x, 64, z);
                assertEquals(expected, actual, "x=" + x + " z=" + z);
            }
        }
    }

    @Test
    void oneBlockShiftIsAColdFill() {
        final World world = mockWorld(true);
        final BiomeGenBase biome = fixedFogBiome(world, 0x123456);

        FogColourGrid.colour(world, 100, 64, 200);
        FogColourGrid.colour(world, 100, 64, 200);
        BopFog.verifyCall(biome, times(1));

        FogColourGrid.colour(world, 101, 64, 200);
        BopFog.verifyCall(biome, times(2));
    }

    @Test
    void nonCacheableCellIsRequeried() {
        final World world = mockWorld(false);
        final BiomeGenBase biome = fixedFogBiome(world, 0x654321);

        FogColourGrid.colour(world, 5, 64, 9);
        FogColourGrid.colour(world, 5, 64, 9);

        BopFog.verifyCall(biome, times(2));
    }

    @Test
    void worldSwapRequeries() {
        final World worldA = mockWorld(true);
        fixedFogBiome(worldA, 0x111111);
        final int a = FogColourGrid.colour(worldA, 7, 64, 3);
        assertEquals(0x111111, a);

        FogColourGrid.invalidate();

        final World worldB = mockWorld(true);
        final BiomeGenBase biomeB = fixedFogBiome(worldB, 0x222222);
        final int b = FogColourGrid.colour(worldB, 7, 64, 3);
        assertEquals(0x222222, b);
        BopFog.verifyCall(biomeB, times(1));
    }
}
