package com.prupe.mcpatcher.ctm;

import net.minecraft.block.Block;

/**
 * Implement this interface on blocks that support connected textures to allow them to control if they connect to neighbors or not
 */
public interface ICTMBlock {
    boolean shouldConnectByBlock(RenderBlockState renderBlockState, Block neighbor, int neighborX, int neighborY, int neighborZ);
}
