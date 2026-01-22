package com.gtnewhorizons.angelica.rendering.celeritas.light;

import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;

@Setter
public class LightDataCache extends LightDataAccess {
    private IBlockAccess world;

    @Override
    protected int compute(int x, int y, int z) {
        final IBlockAccess world = this.world;
        final Block block = world.getBlock(x, y, z);

        final boolean fo = block.isOpaqueCube();
        final boolean fc = block.isNormalCube();
        final int lu = block.getLightValue(world, x, y, z);

        // Optimize: skip light calculation for opaque blocks with no luminance
        final int bl, sl;
        if (fo && lu == 0) {
            bl = 0;
            sl = 0;
        } else {
            final int packed = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
            bl = (packed >> 4) & 0xF;
            sl = (packed >> 20) & 0xF;
        }

        // AO value - blocks that emit light don't contribute to AO
        final float ao = lu == 0 ? block.getAmbientOcclusionLightValue() : 1.0f;

        // Opacity check - true if block is view-blocking (solid blocks)
        final boolean op = block.getMaterial().isOpaque() && block.getLightOpacity() > 0;

        // Emissive check - full bright if lightmap coords would be max (15, 15)
        final boolean em = lu == 15;

        return packFC(fc) | packFO(fo) | packOP(op) | packEM(em) | packAO(ao) | packLU(lu) | packSL(sl) | packBL(bl);
    }
}
