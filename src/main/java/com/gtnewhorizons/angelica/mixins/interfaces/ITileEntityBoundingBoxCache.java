package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.util.AxisAlignedBB;

public interface ITileEntityBoundingBoxCache {
    AxisAlignedBB angelica$getCachedRenderBoundingBox();

    boolean angelica$isInfiniteExtent();
}
