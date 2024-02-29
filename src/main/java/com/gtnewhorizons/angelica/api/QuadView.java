package com.gtnewhorizons.angelica.api;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadViewMutable;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraftforge.common.util.ForgeDirection;

import javax.annotation.Nullable;

public interface QuadView extends ModelQuadViewMutable {

    boolean isShade();
    boolean isDeleted();
    ForgeDirection getFace();
    QuadView copyFrom(QuadView src);
    void setRaw(int[] data, boolean shade, @Nullable ForgeDirection face, int colorIndex, int flags);
    int[] getRawData();

    /**
     * Present for compatibility with the Tesselator, not recommended for general use.
     */
    void setState(int[] rawBuffer, int offset, BlockRenderer.Flags flags, int drawMode, float offsetX, float offsetY, float offsetZ);
}
