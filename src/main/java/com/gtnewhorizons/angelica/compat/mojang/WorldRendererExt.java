package com.gtnewhorizons.angelica.compat.mojang;

public interface WorldRendererExt {
    static int getLightmapCoordinates(BlockRenderView world, BlockState state, BlockPos pos) {
        return 15;
    }

}
