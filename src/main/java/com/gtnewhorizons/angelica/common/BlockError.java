package com.gtnewhorizons.angelica.common;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

/**
 * This Block is not actually getting registered. A static instance is created in AngelicaMod.
 * In order to register the texture for it, there is a mixin to TextureMap which injects a call
 * to this instance's registerBlocKIcons method. Actually registering the block on the client but
 * not the server can cause block IDs to different between client/server, which is not good.

 * We're essentially injecting the block's texture, so that we may use this block as if it were
 * any normal block for rendering purposes with RenderBlocks and such, but not have to actually
 * register it as a block.
 */

public class BlockError extends Block {

    public static final IIcon[] icons = new IIcon[2];

    public BlockError() {
        super(Material.rock);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg)
    {
        for (int i = 0; i < icons.length; i++) {
            icons[i] = reg.registerIcon("angelica:error_block_" + i);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons[meta];
    }

}
