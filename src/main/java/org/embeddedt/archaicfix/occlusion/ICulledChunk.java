package org.embeddedt.archaicfix.occlusion;

import net.minecraft.world.chunk.Chunk;
import org.embeddedt.archaicfix.occlusion.VisGraph;

public interface ICulledChunk {
    VisGraph[] getVisibility();
    Chunk buildCulledSides();
}
