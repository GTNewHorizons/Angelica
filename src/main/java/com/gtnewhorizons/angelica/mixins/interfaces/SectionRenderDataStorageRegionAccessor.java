package com.gtnewhorizons.angelica.mixins.interfaces;

import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

public interface SectionRenderDataStorageRegionAccessor {
    RenderRegion angelica$getRegion();
    void angelica$setRegion(RenderRegion region);

    int angelica$getPassIndex();
    void angelica$setPassIndex(int passIndex);

    /** Per-section meta slot indexed by localSectionIndex; null when the backend has no GPU culling. */
    int[] angelica$getSlotCache();
}
