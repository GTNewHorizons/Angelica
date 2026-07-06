package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSectionCache;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.joml.Vector3d;

public interface ChunkTaskProvider {
    ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame, Vector3d cameraPosition);

    default ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame, Vector3d cameraPosition, ClonedChunkSectionCache sectionCache) {
        return createRebuildTask(render, frame, cameraPosition);
    }

    /** @return -1 = single-threaded, 0 = auto-detect, >0 = specific count */
    int threadCount();

    /** Lower = higher priority */
    int priority();

    default boolean isEnabled() {
        return true;
    }
}
