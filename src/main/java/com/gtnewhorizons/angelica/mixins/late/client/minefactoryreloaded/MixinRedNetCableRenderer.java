package com.gtnewhorizons.angelica.mixins.late.client.minefactoryreloaded;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import powercrystals.minefactoryreloaded.render.block.RedNetCableRenderer;
import powercrystals.minefactoryreloaded.setup.MFRConfig;
import powercrystals.minefactoryreloaded.tile.rednet.TileEntityRedNetCable;

@Mixin(value = RedNetCableRenderer.class, remap = false)
public abstract class MixinRedNetCableRenderer extends TileEntitySpecialRenderer {

    @Shadow
    private boolean renderCable(IBlockAccess world, int x, int y, int z, int brightness, TileEntityRedNetCable _cable) {
        throw new AbstractMethodError("Shadow");
    }

    /**
     * @author JL2210
     * @reason Thread safety compat (check getTileEntity for null)
     */
    @Overwrite
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
            RenderBlocks renderer) {
        TileEntityRedNetCable _cable = (TileEntityRedNetCable) world.getTileEntity(x, y, z);

        // rewrite as injector?
        // orig: if (MFRConfig.TESRCables && _cable.onRender())
        if (_cable == null || (MFRConfig.TESRCables && _cable.onRender())) return false;

        int brightness = block.getMixedBrightnessForBlock(world, x, y, z);

        return renderCable(world, x, y, z, brightness, _cable);
    }
}
