package com.gtnewhorizons.angelica.common;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.function.Supplier;

public class BlockTesrTranslucentTest extends Block {

    private final Supplier<TileEntity> tile;

    public BlockTesrTranslucentTest(Supplier<TileEntity> tile) {
        super(Material.glass);
        this.tile = tile;
        setHardness(0.3f);
        setLightOpacity(0);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return tile.get();
    }
}
