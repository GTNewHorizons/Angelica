package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import net.minecraft.block.Block;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

/** Try to replicate modern behavior for when shaderpacks are in use.*/
public final class TileEntityLight {

    private TileEntityLight() {}

    public static int packedLight(World world, int x, int y, int z, int vanillaLight) {
        final Block block = world.getBlock(x, y, z);
        if (!block.getUseNeighborBrightness() || block.getLightOpacity() != 0) return vanillaLight;
        final int sky = world.provider.hasNoSky ? 0 : world.getSavedLightValue(EnumSkyBlock.Sky, x, y, z);
        final int blockLight = Math.max(world.getSavedLightValue(EnumSkyBlock.Block, x, y, z), block.getLightValue(world, x, y, z));
        return DynamicLights.addDynamicLight(world, x, y, z, sky << 20 | blockLight << 4);
    }
}
