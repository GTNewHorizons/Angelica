package net.coderbot.iris.block_rendering;

import net.minecraft.block.Block;

public interface MaterialIdLookup {

    /** Looks up a block's material id for the matching shaderpack. */
    short get(Block block, int meta);
}
