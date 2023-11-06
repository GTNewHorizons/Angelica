package com.gtnewhorizons.angelica.compat.mojang;

import javax.annotation.Nullable;

public interface BlockColorProvider {
    int getColor(BlockState state, @Nullable BlockRenderView world, @Nullable BlockPos pos, int tintIndex);
}
