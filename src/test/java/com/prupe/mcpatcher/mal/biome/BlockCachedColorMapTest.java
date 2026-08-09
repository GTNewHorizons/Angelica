package com.prupe.mcpatcher.mal.biome;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockCachedColorMapTest {

    private static final class CountingMap implements IColorMap {

        private final float[] scratch = new float[3];
        int calls;

        @Override
        public boolean isHeightDependent() {
            return true;
        }

        @Override
        public int getColorMultiplier() {
            return 0;
        }

        @Override
        public int getColorMultiplier(IBlockAccess blockAccess, int i, int j, int k) {
            return ColorUtils.float3ToInt(getColorMultiplierF(blockAccess, i, j, k));
        }

        @Override
        public float[] getColorMultiplierF(IBlockAccess blockAccess, int i, int j, int k) {
            calls++;
            scratch[0] = i;
            scratch[1] = j;
            scratch[2] = k;
            return scratch;
        }

        @Override
        public void claimResources(Collection<ResourceLocation> resources) {}

        @Override
        public IColorMap copy() {
            return new CountingMap();
        }
    }

    private final IBlockAccess world = Mockito.mock(IBlockAccess.class);

    @Test
    void repeatQueriesHitTheCache() {
        final CountingMap parent = new CountingMap();
        final ColorMapBase.BlockCached cached = new ColorMapBase.BlockCached(parent);

        final float[] first = cached.getColorMultiplierF(world, 10, 64, -3);
        assertEquals(1, parent.calls);
        assertArrayEquals(new float[] { 10, 64, -3 }, first);

        cached.getColorMultiplierF(world, 10, 64, -3);
        assertEquals(1, parent.calls, "second query for the same block must not hit the parent");
    }

    @Test
    void cachedValueSurvivesParentScratchReuse() {
        final CountingMap parent = new CountingMap();
        final ColorMapBase.BlockCached cached = new ColorMapBase.BlockCached(parent);

        final float[] a = cached.getColorMultiplierF(world, 1, 64, 1);
        cached.getColorMultiplierF(world, 2, 64, 2);
        assertArrayEquals(new float[] { 1, 64, 1 }, a, "parent scratch reuse must not corrupt cached entries");
        assertArrayEquals(new float[] { 1, 64, 1 }, cached.getColorMultiplierF(world, 1, 64, 1));
        assertEquals(2, parent.calls);
    }

    @Test
    void distinctCoordinatesAreDistinctEntries() {
        final CountingMap parent = new CountingMap();
        final ColorMapBase.BlockCached cached = new ColorMapBase.BlockCached(parent);

        cached.getColorMultiplierF(world, -1, 64, 0);
        cached.getColorMultiplierF(world, 0, 64, -1);
        cached.getColorMultiplierF(world, -1, 63, 0);
        assertEquals(3, parent.calls, "negative/asymmetric coords must not collide in the packed key");
    }

    @Test
    void worldChangeClearsTheCache() {
        final CountingMap parent = new CountingMap();
        final ColorMapBase.BlockCached cached = new ColorMapBase.BlockCached(parent);

        cached.getColorMultiplierF(world, 5, 64, 5);
        assertEquals(1, parent.calls);

        final IBlockAccess otherWorld = Mockito.mock(IBlockAccess.class);
        cached.getColorMultiplierF(otherWorld, 5, 64, 5);
        assertEquals(2, parent.calls, "a different world must not serve cached colors");
    }
}
