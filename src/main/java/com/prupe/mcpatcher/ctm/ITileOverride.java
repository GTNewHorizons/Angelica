package com.prupe.mcpatcher.ctm;

import java.util.List;
import java.util.Set;

import net.minecraft.util.IIcon;

import com.prupe.mcpatcher.mal.block.BlockStateMatcher;

interface ITileOverride extends Comparable<ITileOverride> {

    boolean isDisabled();

    void registerIcons();

    List<BlockStateMatcher> getMatchingBlocks();

    Set<String> getMatchingTiles();

    int getRenderPass();

    int getWeight();

    IIcon getTileWorld(RenderBlockState renderBlockState, IIcon origIcon);

    IIcon getTileHeld(RenderBlockState renderBlockState, IIcon origIcon);
}
