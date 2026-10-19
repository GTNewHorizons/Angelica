package com.gtnewhorizons.angelica.compat.bop;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FogBiomeCacheTest {

    private static World worldWith(BiomeGenBase biome, boolean chunkLoaded) {
        final World world = Mockito.mock(World.class);
        final IChunkProvider provider = Mockito.mock(IChunkProvider.class);
        when(world.getBiomeGenForCoords(anyInt(), anyInt())).thenReturn(biome);
        when(world.getChunkProvider()).thenReturn(provider);
        when(provider.chunkExists(anyInt(), anyInt())).thenReturn(chunkLoaded);
        return world;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lookupCounts")
    void worldLookupsPerCoordinateList(String label, boolean chunkLoaded, int[][] xz, int expectedWorldLookups) {
        final BiomeGenBase biome = Mockito.mock(BiomeGenBase.class);
        final World world = worldWith(biome, chunkLoaded);

        for (int[] c : xz) {
            assertSame(biome, FogBiomeCache.get(world, c[0], c[1]));
        }
        verify(world, times(expectedWorldLookups)).getBiomeGenForCoords(anyInt(), anyInt());
    }

    private static Stream<Arguments> lookupCounts() {
        return Stream.of(
            Arguments.of("repeat lookups hit the cache", true, new int[][] {{100, -7}, {100, -7}}, 1),
            Arguments.of("unloaded chunks are not cached", false, new int[][] {{5000, 5000}, {5000, 5000}}, 2),
            Arguments.of("negative coordinates do not collide", true, new int[][] {{-1, 0}, {0, -1}, {-1, -1}}, 3));
    }

    @Test
    void worldChangeClearsTheCache() {
        final BiomeGenBase a = Mockito.mock(BiomeGenBase.class);
        final BiomeGenBase b = Mockito.mock(BiomeGenBase.class);
        final World worldA = worldWith(a, true);
        final World worldB = worldWith(b, true);

        assertSame(a, FogBiomeCache.get(worldA, 0, 0));
        assertSame(b, FogBiomeCache.get(worldB, 0, 0), "same coords in a new world must re-resolve");
        assertSame(a, FogBiomeCache.get(worldA, 0, 0), "switching back re-resolves rather than serving worldB state");
    }
}
