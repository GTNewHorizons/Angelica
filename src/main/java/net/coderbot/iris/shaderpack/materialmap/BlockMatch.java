package net.coderbot.iris.shaderpack.materialmap;

import net.minecraft.block.Block;

public class BlockMatch {
    private final Block block;
    private final Integer meta;

    public BlockMatch(Block block, Integer meta) {
        this.block = block;
        this.meta = meta;
    }

    public boolean matches(Block block, int meta) {
        return this.block == block && (this.meta == null || this.meta == meta);
    }
}
