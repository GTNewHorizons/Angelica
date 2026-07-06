package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaMainThreadMeshingTask;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.joml.Vector3d;

public final class DefaultChunkTaskProvider implements ChunkTaskProvider {
    public static final DefaultChunkTaskProvider INSTANCE = new DefaultChunkTaskProvider();

    private DefaultChunkTaskProvider() {}

    @Override
    public ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame, Vector3d cameraPosition) {
        return new AngelicaMainThreadMeshingTask(render, frame, cameraPosition);
    }

    @Override
    public int threadCount() {
        return -1;
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
