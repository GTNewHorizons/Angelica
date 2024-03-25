package com.gtnewhorizons.angelica.testing;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockTESR extends Block implements ITileEntityProvider {

    public static IIcon cableIcon;

    /** Render ID of this block type */
    private static int renderID;
    /**
     * Set the render ID of this block type
     *
     * @param id New ID
     */
    public static void setRenderID(int id) {
        renderID = id;
    }

    /**
     * @return render ID of this block type
     */
    public static int getRenderID() {
        return renderID;
    }

    /**
     * @return render type of this block instance
     */
    @Override
    public int getRenderType() {
        return renderID;
    }

    public BlockTESR() {
        super(Material.rock);
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {

        cableIcon = reg.registerIcon("angelica:se_cable");
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileTESR();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

}
