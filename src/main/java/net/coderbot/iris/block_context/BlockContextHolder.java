package net.coderbot.iris.block_context;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.block.Block;

/**
 * Holds block context for shader material ID lookups.
 */
public class BlockContextHolder {
    private final Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches;
    public int blockId;
    public short renderType;

    public BlockContextHolder(Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches) {
        this.blockMetaMatches = blockMetaMatches;
        this.blockId = -1;
        this.renderType = -1;
    }

    /**
     * Set the material ID for a block.
     * @param block The block
     * @param meta The block metadata
     * @param renderType The render type
     */
    public void set(Block block, int meta, short renderType) {
        Int2IntMap metaMap = this.blockMetaMatches != null ? this.blockMetaMatches.get(block) : null;
        int id = metaMap != null ? metaMap.get(meta) : -1;

        this.blockId = id;
        this.renderType = renderType;
    }

    public void reset() {
        this.blockId = -1;
        this.renderType = -1;
    }
}
