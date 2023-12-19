package com.prupe.mcpatcher.mal.biome;

import java.util.Collection;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

public interface IColorMap {

    boolean isHeightDependent();

    int getColorMultiplier();

    int getColorMultiplier(IBlockAccess blockAccess, int i, int j, int k);

    float[] getColorMultiplierF(IBlockAccess blockAccess, int i, int j, int k);

    void claimResources(Collection<ResourceLocation> resources);

    IColorMap copy();
}
