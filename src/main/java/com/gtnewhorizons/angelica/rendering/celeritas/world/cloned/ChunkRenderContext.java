package com.gtnewhorizons.angelica.rendering.celeritas.world.cloned;

import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

public class ChunkRenderContext {

    private final ChunkSectionPos origin;
    private final ClonedChunkSection[] sections;
    private final StructureBoundingBox volume;

    public ChunkRenderContext(ChunkSectionPos origin, ClonedChunkSection[] sections, StructureBoundingBox volume) {
        this.origin = origin;
        this.sections = sections;
        this.volume = volume;
    }

    public ChunkSectionPos getOrigin() {
        return this.origin;
    }

    public ClonedChunkSection[] getSections() {
        return this.sections;
    }

    public StructureBoundingBox getVolume() {
        return this.volume;
    }
}
