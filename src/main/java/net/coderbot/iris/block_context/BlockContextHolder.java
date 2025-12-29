package net.coderbot.iris.block_context;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;

public class BlockContextHolder {
    private final Object2IntMap<Block> blockMatches;
    public int blockId;
    public short renderType;

    public BlockContextHolder(Object2IntMap<Block> blockMatches) {
        this.blockMatches = blockMatches;
    }

    public void set(Block block, short renderType) {
        this.renderType = renderType;
        this.blockId = blockMatches.getOrDefault(block, -1);
    }
}
