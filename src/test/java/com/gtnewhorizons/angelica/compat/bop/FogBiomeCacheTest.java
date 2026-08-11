package com.gtnewhorizons.angelica.compat.bop;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

    @Test
    void repeatLookupsHitTheCache() {
        final BiomeGenBase biome = Mockito.mock(BiomeGenBase.class);
        final World world = worldWith(biome, true);

        assertSame(biome, FogBiomeCache.get(world, 100, -7));
        assertSame(biome, FogBiomeCache.get(world, 100, -7));
        verify(world, times(1)).getBiomeGenForCoords(anyInt(), anyInt());
    }

    @Test
    void unloadedChunksAreNotCached() {
        final BiomeGenBase biome = Mockito.mock(BiomeGenBase.class);
        final World world = worldWith(biome, false);

        FogBiomeCache.get(world, 5000, 5000);
        FogBiomeCache.get(world, 5000, 5000);
        verify(world, times(2)).getBiomeGenForCoords(anyInt(), anyInt());
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

    @Test
    void negativeCoordinatesDoNotCollide() {
        final BiomeGenBase biome = Mockito.mock(BiomeGenBase.class);
        final World world = worldWith(biome, true);

        FogBiomeCache.get(world, -1, 0);
        FogBiomeCache.get(world, 0, -1);
        FogBiomeCache.get(world, -1, -1);
        verify(world, times(3)).getBiomeGenForCoords(anyInt(), anyInt());
    }
}
