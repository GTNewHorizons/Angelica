package com.gtnewhorizons.angelica.dynamiclights;

/**
 * Common interface for world renderers that support dynamic lights chunk rebuilds.
 * Implemented by CeleritasWorldRenderer.
 */
public interface IDynamicLightWorldRenderer {
    /**
     * Schedules a chunk rebuild for the render section at the given chunk coordinates.
     */
    void scheduleRebuildForChunk(int x, int y, int z, boolean important);

    /**
     * Returns whether this renderer is currently active (has a world loaded).
     */
    boolean isActive();
}
