package com.gtnewhorizons.angelica.common;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * This Block is not actually getting registered. A static instance is created in AngelicaMod.
 * Actually registering the block on the client but not the server can cause block IDs to
 * different between client/server, which is not good.

 * We're essentially injecting the block's texture, so that we may use this block as if it were
 * any normal block for rendering purposes with RenderBlocks and such, but not have to actually
 * register it as a block.
 */

public class BlockError extends Block {

    public static final IIcon[] icons = new IIcon[2];

    public BlockError() {
        super(Material.rock);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) {
            for (int i = 0; i < icons.length; i++) {
                icons[i] = event.map.registerIcon("angelica:error_block_" + i);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return meta >= 0 && meta < icons.length ? icons[meta] : null;
    }

}
