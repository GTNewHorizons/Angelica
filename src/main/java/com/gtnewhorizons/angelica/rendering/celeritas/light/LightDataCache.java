package com.gtnewhorizons.angelica.rendering.celeritas.light;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;

@Setter
public class LightDataCache extends LightDataAccess {
    private IBlockAccess world;

    /**
     * Doing this to force packs like Steadfast's water to work properly.
     */
    private static int skyLight(IBlockAccess world, Block block, int x, int y, int z, int neighborMaxed) {
        if (world instanceof WorldSlice slice && block.getMaterial().isLiquid()) {
            return slice.getRawSkyLight(x, y, z);
        }
        return neighborMaxed;
    }

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
            sl = skyLight(world, block, x, y, z, (packed >> 20) & 0xF);
        }

        // AO value - blocks that emit light don't contribute to AO
        final float ao = lu == 0 ? block.getAmbientOcclusionLightValue() : 1.0f;

        // Opacity check - true if block is view-blocking (solid blocks)
        final boolean op = block.getMaterial().isOpaque() && block.getLightOpacity() > 0;

        return packFC(fc) | packFO(fo) | packOP(op) | packAO(ao) | packLU(lu) | packSL(sl) | packBL(bl);
    }
}
