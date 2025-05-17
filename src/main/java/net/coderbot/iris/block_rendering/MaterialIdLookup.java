package net.coderbot.iris.block_rendering;

import net.minecraft.block.Block;

/** An object which can find material ids for blocks within the context of a shaderpack. */
public interface MaterialIdLookup {

    /** Looks up a block's material id for the current shaderpack. */
    short get(Block block, int meta);
}
