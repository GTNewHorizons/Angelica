package com.gtnewhorizons.angelica.api.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;

/** TESR mesh cache entry points. Render thread only. */
public final class TesrMeshCache {

    private TesrMeshCache() {}

    public static void renderCached(Object key, TesrMeshBuilder builder) {
        AngelicaTesrMeshCache.INSTANCE.renderCached(key, builder);
    }

    /** Manual invalidate ande force rebuild on next render */
    public static void invalidate(Object key) {
        AngelicaTesrMeshCache.INSTANCE.invalidate(key);
    }
}
