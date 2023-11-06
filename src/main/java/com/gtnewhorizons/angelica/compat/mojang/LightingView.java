package com.gtnewhorizons.angelica.compat.mojang;

public interface LightingView {
    default void setSectionStatus(BlockPos pos, boolean notReady) {
        this.setSectionStatus(ChunkSectionPos.from(pos), notReady);
    }

    void setSectionStatus(ChunkSectionPos pos, boolean notReady);
}
