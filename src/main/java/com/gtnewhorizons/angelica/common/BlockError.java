package com.gtnewhorizons.angelica.common;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;

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

    public BlockError() {
        super(Material.rock);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg)
    {
        this.blockIcon = reg.registerIcon("angelica:error_block");
    }

}
