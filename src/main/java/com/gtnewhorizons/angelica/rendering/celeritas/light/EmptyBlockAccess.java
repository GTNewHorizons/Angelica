package com.gtnewhorizons.angelica.rendering.celeritas.light;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

public enum EmptyBlockAccess implements IBlockAccess {
    INSTANCE;

    @Override
    public Block getBlock(int x, int y, int z) {
        return Blocks.air;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return 0;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return null;
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) {
        return min << 4;
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int direction) {
        return 0;
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return true;
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return BiomeGenBase.plains;
    }

    @Override
    public int getHeight() {
        return 256;
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
        return false;
    }
}
