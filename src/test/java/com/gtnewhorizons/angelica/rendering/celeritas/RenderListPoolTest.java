package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderListPoolTest {

    @Test
    void consecutiveGenerationsNeverShareArray() {
        RenderListPool pool = new RenderListPool();
        ChunkRenderList[] prev = pool.beginSearch(1);
        for (int i = 2; i < 40; i++) {
            ChunkRenderList[] cur = pool.beginSearch(i % 7 == 0 ? i : 3);
            assertNotSame(prev, cur);
            prev = cur;
        }
    }

    @Test
    void slotGrowsKeepingEntries() {
        RenderListPool pool = new RenderListPool();
        ChunkRenderList[] small = pool.beginSearch(2);
        pool.beginSearch(2);
        RenderRegion region = region(1);
        ChunkRenderList list = RenderListPool.acquire(small, region);

        ChunkRenderList[] grown = pool.beginSearch(16);
        assertTrue(grown.length >= 16);
        assertSame(list, grown[1]);

        pool.beginSearch(16);
        assertSame(grown, pool.beginSearch(8));
    }

    @Test
    void acquireStoresNewListAndReplacesForeignRegion() {
        ChunkRenderList[] slot = new ChunkRenderList[4];
        RenderRegion first = region(2);
        ChunkRenderList a = RenderListPool.acquire(slot, first);
        assertSame(a, slot[2]);
        assertSame(first, a.getRegion());

        RenderRegion reused = region(2);
        ChunkRenderList b = RenderListPool.acquire(slot, reused);
        assertNotSame(a, b);
        assertSame(b, slot[2]);
        assertSame(reused, b.getRegion());
        assertEquals(0, b.size());
    }

    @Test
    void resetCoversExactlyTheDeclaredMutableIntFields() {
        final Set<String> expected = new HashSet<>(
            Arrays.asList("sectionsWithGeometryCount", "sectionsWithSpritesCount", "sectionsWithEntitiesCount",
                "sectionsNeedingDynamicSortCount", "size"));
        final Set<String> actual = new HashSet<>();
        for (Field f : ChunkRenderList.class.getDeclaredFields()) {
            if (f.getType() != int.class) continue;
            final int mods = f.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) continue;
            actual.add(f.getName());
        }
        assertEquals(expected, actual);
    }

    private static RenderRegion region(int id) {
        final RenderRegion region = Reflect.allocate(RenderRegion.class);
        Reflect.set(region, "id", id);
        return region;
    }
}
