package com.gtnewhorizons.angelica.render;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;

public class ErrorBlock extends Block {

    public ErrorBlock() {
        super(Material.rock);

        // This is bad, we're not actually registering this block, we're initializing a static instance of it
        // during init phase(not pre-init, the texture map will be null then), that we're using to render this
        // manually with RenderBlocks, in order to draw it in error states.
        Minecraft mc = Minecraft.getMinecraft();
        this.blockIcon = mc.getTextureMapBlocks().registerIcon("angelica:error_block");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int id, int meta) {
        return this.blockIcon;
    }
}
