package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.coderbot.iris.block_rendering.NbtConditionalIdMap;
import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
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
    public final long priority;
    public final long workerNanos;
    public DeferredCapture capture;
    public long mainNanos;

    public DeferredSectionMesh(AngelicaChunkBuilderMeshingTask task, CancellationToken token, ChunkBuildBuffers buffers, WorldSlice slice, AngelicaBuiltRenderSectionData renderData, List<DeferredBlock> blocks, NbtConditionalIdMap<Block> teMap, long currentTick, boolean important, long priority, long workerNanos) {
        this.task = task;
        this.token = token;
        this.buffers = buffers;
        this.slice = slice;
        this.renderData = renderData;
        this.blocks = blocks;
        this.teMap = teMap;
        this.currentTick = currentTick;
        this.important = important;
        this.priority = priority;
        this.workerNanos = workerNanos;
    }

    public boolean isObsolete() {
        return token.isCancelled() || task.render.isDisposed() || task.render.getLastBuiltFrame() > task.buildTime;
    }

    /** Main thread only; returns a failure or null. */
    public ChunkJobResult<ChunkBuildOutput> capture() {
        return task.captureDeferred(this);
    }

    public ChunkBuildOutput finish(ChunkBuildContext context) {
        return task.finishDeferred(this, (AngelicaChunkBuildContext) context);
    }

    public static final class DeferredCapture {
        public static final int VERTEX_TRIM = 1 << 17;

        public final IntArrayList raw = new IntArrayList();
        public final IntArrayList vertexStates = new IntArrayList();
        public final IntArrayList shaderBlockIds = new IntArrayList();
        public final IntArrayList blockIndex = new IntArrayList();
        public final IntArrayList blockStart = new IntArrayList();
        public final IntArrayList blockVertexCount = new IntArrayList();
        public final IntArrayList teBlockIds = new IntArrayList();

        public void add(int blockIdx, int[] rawBuffer, int vertexCount, int[] states, int[] shaderIds, int teBlockId) {
            blockIndex.add(blockIdx);
            blockStart.add(vertexStates.size());
            blockVertexCount.add(vertexCount);
            teBlockIds.add(teBlockId);
            raw.addElements(raw.size(), rawBuffer, 0, vertexCount * AngelicaChunkBuildContext.INTS_PER_VERTEX);
            vertexStates.addElements(vertexStates.size(), states, 0, vertexCount);
            shaderBlockIds.addElements(shaderBlockIds.size(), shaderIds, 0, vertexCount);
        }

        public int blockCount() {
            return blockIndex.size();
        }

        public int vertexCount() {
            return vertexStates.size();
        }

        public void clear() {
            reset(raw, VERTEX_TRIM * AngelicaChunkBuildContext.INTS_PER_VERTEX);
            reset(vertexStates, VERTEX_TRIM);
            reset(shaderBlockIds, VERTEX_TRIM);
            reset(blockIndex, VERTEX_TRIM);
            reset(blockStart, VERTEX_TRIM);
            reset(blockVertexCount, VERTEX_TRIM);
            reset(teBlockIds, VERTEX_TRIM);
        }

        private static void reset(IntArrayList list, int cap) {
            list.clear();
            if (list.elements().length > cap) list.trim(cap);
        }
    }
}
