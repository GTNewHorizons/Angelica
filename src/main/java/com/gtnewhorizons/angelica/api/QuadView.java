package com.gtnewhorizons.angelica.api;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadViewMutable;
import me.jellysquid.mods.sodium.client.model.quad.Quad;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraftforge.common.util.ForgeDirection;

public interface QuadView extends ModelQuadViewMutable {

    /**
     * Allocates a new quad - use sparingly, and not at all in render paths if you can help it. We don't need another
     * Malice Doors.
     */
    static QuadView allocate() {
        return new Quad();
    }

    boolean isShade();
    boolean isDeleted();
    ForgeDirection getFace();
    QuadView copyFrom(QuadView src);
    int[] getRawData();

    /**
     * Present for compatibility with the Tesselator, not recommended for general use.
     */
    void setState(int[] rawBuffer, int offset, BlockRenderer.Flags flags, int drawMode, float offsetX, float offsetY, float offsetZ);
}
