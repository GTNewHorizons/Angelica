package me.jellysquid.mods.sodium.client.world.cloned;

import com.gtnewhorizons.angelica.compat.ChunkSectionPos;
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

    public ClonedChunkSection[] getSections() {
        return this.sections;
    }

    public ChunkSectionPos getOrigin() {
        return this.origin;
    }

    public StructureBoundingBox getVolume() {
        return this.volume;
    }

    public void releaseResources() {
        for (ClonedChunkSection section : sections) {
            if (section != null) {
                section.getBackingCache().release(section);
            }
        }
    }
}
