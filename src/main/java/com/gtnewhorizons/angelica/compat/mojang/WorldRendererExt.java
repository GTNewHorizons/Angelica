package com.gtnewhorizons.angelica.compat.mojang;

import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.block.Block;
import net.minecraft.world.EnumSkyBlock;

public interface WorldRendererExt {
    static int getLightmapCoordinates(WorldSlice world, Block block, BlockPos pos) {

        if (block.getLightValue() > 0) {
            return 0xF000F0;
        }

        int sl = world.getLightLevel(EnumSkyBlock.Sky, pos.x, pos.y, pos.z);
        int bl = world.getLightLevel(EnumSkyBlock.Block, pos.x, pos.y, pos.z);
        bl += sl;
        return sl << 20 | bl << 4;
    }

}
