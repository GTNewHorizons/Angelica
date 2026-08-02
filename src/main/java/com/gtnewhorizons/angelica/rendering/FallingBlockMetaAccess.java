package com.gtnewhorizons.angelica.rendering;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;


public final class FallingBlockMetaAccess implements IBlockAccess {

    private IBlockAccess delegate;
    private int x;
    private int y;
    private int z;
    private int metadata;

    public FallingBlockMetaAccess set(IBlockAccess delegate, int x, int y, int z, int metadata) {
        this.delegate = delegate;
        this.x = x;
        this.y = y;
        this.z = z;
        this.metadata = metadata;
        return this;
    }

    public void clear() {
        this.delegate = null;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        if (x == this.x && y == this.y && z == this.z) {
            return this.metadata;
        }
        return delegate.getBlockMetadata(x, y, z);
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return delegate.getBlock(x, y, z);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return delegate.getTileEntity(x, y, z);
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int lightValue) {
        return delegate.getLightBrightnessForSkyBlocks(x, y, z, lightValue);
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int direction) {
        return delegate.isBlockProvidingPowerTo(x, y, z, direction);
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return delegate.isAirBlock(x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return delegate.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return delegate.extendedLevelsInChunkCache();
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
        return delegate.isSideSolid(x, y, z, side, _default);
    }
}
