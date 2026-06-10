package net.coderbot.iris.block_rendering;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.junit.jupiter.api.Test;

import static net.coderbot.iris.block_rendering.BlockMaterialMapping.WILDCARD_META_KEY;
import static net.coderbot.iris.block_rendering.BlockMaterialMapping.lookupBlockId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the wildcard-aware lookup contract of {@link BlockMaterialMapping#lookupBlockId} independent
 * of any render path. The per-block maps are plain {@link Int2IntMap}s (default return value -1),
 * so the contract can be exercised without the Minecraft registry.
 */
class BlockMaterialMappingTest {

    private static Int2IntMap map() {
        final Int2IntMap m = new Int2IntOpenHashMap();
        m.defaultReturnValue(-1);
        return m;
    }

    @Test
    void noMetaEntryMatchesAnyMetadata() {
        // A no-meta entry registers only the wildcard key (mirrors applyMetas with extraBits == 0).
        final Int2IntMap m = map();
        m.put(WILDCARD_META_KEY, 10030);

        assertEquals(10030, lookupBlockId(m, 0), "meta 0 should match the no-meta entry");
        assertEquals(10030, lookupBlockId(m, 5), "meta 5 should match the no-meta entry");
        assertEquals(10030, lookupBlockId(m, 8855), "extended meta 8855 should match the no-meta entry");
    }

    @Test
    void exactEntryWinsOverWildcardForItsMeta() {
        // Both a no-meta entry (wildcard -> 100) and an exact :5 entry (key 5 -> 200) for one block.
        final Int2IntMap m = map();
        m.put(WILDCARD_META_KEY, 100);
        m.put(5, 200);

        assertEquals(200, lookupBlockId(m, 5), "exact meta should win over the wildcard");
        assertEquals(100, lookupBlockId(m, 3), "other low metas fall back to the wildcard");
        assertEquals(100, lookupBlockId(m, 8855), "extended metas fall back to the wildcard");
    }

    @Test
    void exactOnlyEntryDoesNotMatchUnlistedMeta() {
        final Int2IntMap m = map();
        m.put(5, 200);

        assertEquals(200, lookupBlockId(m, 5), "listed meta matches");
        assertEquals(-1, lookupBlockId(m, 8855), "unlisted meta returns -1 (no wildcard present)");
        assertEquals(-1, lookupBlockId(m, 0), "unlisted meta returns -1 (no wildcard present)");
    }

    @Test
    void snowyStyleNoMetaEntryRegistersNoWildcard() {
        // Mirrors applyMetas with extraBits != 0 (snowy / double-plant): 0..15 | bit keys, no wildcard.
        final int bit = BlockMaterialMapping.SNOWY_META_BIT;
        final Int2IntMap m = map();
        for (int meta = 0; meta < 16; meta++) m.put(meta | bit, 42);

        assertFalse(m.containsKey(WILDCARD_META_KEY), "extra-bits entries must not register a wildcard");
        assertEquals(42, lookupBlockId(m, 5 | bit), "snow-covered meta (with bit set) matches");
        assertEquals(-1, lookupBlockId(m, 5), "raw meta without the bit does not match");
        assertEquals(-1, lookupBlockId(m, 8855), "extended meta does not match an extra-bits entry");
    }
}
