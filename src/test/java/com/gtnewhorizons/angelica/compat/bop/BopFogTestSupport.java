package com.gtnewhorizons.angelica.compat.bop;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class BopFogTestSupport {

    private BopFogTestSupport() {
    }

    static final class BopFog {

        private static final Class<?> TYPE = load();
        private static final Method GET_FOG_COLOUR = method();

        private static Class<?> load() {
            try {
                return Class.forName("biomesoplenty.client.fog.IBiomeFog");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Method method() {
            try {
                return TYPE.getMethod("getFogColour", int.class, int.class, int.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        static BiomeGenBase mockBiome() {
            return (BiomeGenBase) Mockito.mock(BiomeGenBase.class, Mockito.withSettings().extraInterfaces(TYPE));
        }

        static Object stubCall(BiomeGenBase biome) {
            try {
                return GET_FOG_COLOUR.invoke(biome, anyInt(), anyInt(), anyInt());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static void verifyCall(BiomeGenBase biome, VerificationMode mode) {
            try {
                GET_FOG_COLOUR.invoke(verify(biome, mode), anyInt(), anyInt(), anyInt());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    record Grid(long seed, int fogPercent) {

        boolean isFog(int x, int z) {
            return Math.floorMod(mix(x, 0, z), 100) < fogPercent;
        }

        int colorAt(int x, int y, int z) {
            return (int) (mix(x, y, z) >>> 8) & 0xFFFFFF;
        }

        private long mix(int x, int y, int z) {
            long h = seed;
            h = h * 31 + x;
            h = h * 31 + y;
            h = h * 31 + z;
            h ^= h >>> 27;
            h *= 0x9E3779B97F4A7C15L;
            h ^= h >>> 31;
            return h;
        }
    }

    static World mockWorld(Grid grid) {
        final World world = Mockito.mock(World.class);
        final IChunkProvider provider = Mockito.mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(provider);
        when(provider.chunkExists(anyInt(), anyInt())).thenReturn(true);

        final BiomeGenBase fogBiome = BopFog.mockBiome();
        when(BopFog.stubCall(fogBiome)).thenAnswer(inv -> grid.colorAt(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        final BiomeGenBase plainBiome = Mockito.mock(BiomeGenBase.class);

        when(world.getBiomeGenForCoords(anyInt(), anyInt())).thenAnswer(inv -> grid.isFog(inv.getArgument(0), inv.getArgument(1)) ? fogBiome : plainBiome);
        return world;
    }
}
