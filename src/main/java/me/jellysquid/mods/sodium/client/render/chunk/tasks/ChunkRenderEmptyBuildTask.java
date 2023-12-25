package me.jellysquid.mods.sodium.client.render.chunk.tasks;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;

/**
 * A build task which does no computation and always return an empty build result. These tasks are created whenever
 * chunk meshes need to be deleted as the only way to change graphics state is to send a message to the main
 * actor thread. In cases where new chunk renders are being created and scheduled, the scheduler will prefer to just
 * synchronously update the render's data to an empty state to speed things along.
 */
public class ChunkRenderEmptyBuildTask<T extends ChunkGraphicsState> extends ChunkRenderBuildTask<T> {
    private final ChunkRenderContainer<T> render;

    public ChunkRenderEmptyBuildTask(ChunkRenderContainer<T> render) {
        this.render = render;
    }

    @Override
    public ChunkBuildResult<T> performBuild(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        return new ChunkBuildResult<>(this.render, ChunkRenderData.EMPTY);
    }

    @Override
    public void releaseResources() {

    }
}
