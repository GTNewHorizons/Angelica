package org.embeddedt.archaicfix.ducks;

import net.minecraft.world.chunk.Chunk;

public interface IArchaicWorld {
    void arch$markTileEntitiesInChunkForRemoval(Chunk chunk);
}
