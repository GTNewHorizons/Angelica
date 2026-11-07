package com.gtnewhorizons.angelica.common;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockClipPlaneTest extends BlockContainer {

    public BlockClipPlaneTest() {
        super(Material.iron);
        setHardness(0.7f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityClipPlaneTest();
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
}
