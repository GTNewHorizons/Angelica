package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import net.coderbot.iris.block_rendering.NbtConditionalIdMap;
import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;

import java.util.List;

public class DeferredSectionMesh {
    public final AngelicaChunkBuilderMeshingTask task;
    public final CancellationToken token;
    public final ChunkBuildBuffers buffers;
    public final WorldSlice slice;
    public final AngelicaBuiltRenderSectionData renderData;
    public final List<DeferredBlock> blocks;
    public final NbtConditionalIdMap<Block> teMap;
    public final long currentTick;
    public final boolean important;
    public final long workerNanos;

    public DeferredSectionMesh(AngelicaChunkBuilderMeshingTask task, CancellationToken token, ChunkBuildBuffers buffers, WorldSlice slice, AngelicaBuiltRenderSectionData renderData, List<DeferredBlock> blocks, NbtConditionalIdMap<Block> teMap, long currentTick, boolean important, long workerNanos) {
        this.task = task;
        this.token = token;
        this.buffers = buffers;
        this.slice = slice;
        this.renderData = renderData;
        this.blocks = blocks;
        this.teMap = teMap;
        this.currentTick = currentTick;
        this.important = important;
        this.workerNanos = workerNanos;
    }

    public boolean isObsolete() {
        return token.isCancelled() || task.render.isDisposed() || task.render.getLastBuiltFrame() > task.buildTime;
    }

    public ChunkJobResult<ChunkBuildOutput> complete(AngelicaChunkBuildContext ctx) {
        return task.completeDeferred(this, ctx);
    }
}
