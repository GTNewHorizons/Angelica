package me.jellysquid.mods.sodium.client.render.chunk.tasks;

import me.jellysquid.mods.sodium.client.gl.buffer.VertexData;
import me.jellysquid.mods.sodium.client.gl.util.BufferSlice;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBufferSorter;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import net.minecraft.client.util.GlAllocationUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles sorting translucency data in built chunks.
 */
public class ChunkRenderTranslucencySortTask<T extends ChunkGraphicsState> extends ChunkRenderBuildTask<T> {
    private static final BlockRenderPass[] TRANSLUCENT_PASSES = Arrays.stream(BlockRenderPass.VALUES).filter(BlockRenderPass::isTranslucent).toArray(BlockRenderPass[]::new);

    private final ChunkRenderContainer<T> render;
    private final BlockPos offset;
    private final Vec3d camera;

    public ChunkRenderTranslucencySortTask(ChunkRenderContainer<T> render, BlockPos offset, Vec3d camera) {
        this.render = render;
        this.offset = offset;
        this.camera = camera;

    }


    @Override
    public ChunkBuildResult<T> performBuild(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        ChunkRenderData data = this.render.getData();
        if(data.isEmpty())
            return null;

        Map<BlockRenderPass, ChunkMeshData> replacementMeshes = new HashMap<>();
        for(BlockRenderPass pass : TRANSLUCENT_PASSES) {
            ChunkGraphicsState state = this.render.getGraphicsState(pass);
            if(state == null)
                continue;
            ByteBuffer translucencyData = state.getTranslucencyData();
            if(translucencyData == null)
                continue;
            ChunkMeshData translucentMesh = data.getMesh(pass);
            if(translucentMesh == null)
                continue;

            // Make a snapshot of the translucency data to sort
            ByteBuffer sortedData = GlAllocationUtils.allocateByteBuffer(translucencyData.capacity());
            synchronized (translucencyData) {
                sortedData.put(translucencyData);
                translucencyData.position(0);
                translucencyData.limit(translucencyData.capacity());
            }

            sortedData.flip();
            // Sort it and create the new mesh
            ChunkBufferSorter.sortStandardFormat(buffers.getVertexType(), sortedData, sortedData.capacity(), (float) camera.x - offset.getX(), (float)camera.y - offset.getY(), (float)camera.z - offset.getZ());
            ChunkMeshData newMesh = new ChunkMeshData();
            newMesh.setVertexData(new VertexData(sortedData, buffers.getVertexType().getCustomVertexFormat()));
            for(Map.Entry<ModelQuadFacing, BufferSlice> entry : translucentMesh.getSlices()) {
                newMesh.setModelSlice(entry.getKey(), entry.getValue());
            }
            replacementMeshes.put(pass, newMesh);
        }

        if(replacementMeshes.isEmpty())
            return null;

        ChunkBuildResult<T> result = new ChunkBuildResult<>(this.render, data.copyAndReplaceMesh(replacementMeshes));
        result.passesToUpload = replacementMeshes.keySet().toArray(new BlockRenderPass[0]);
        return result;
    }

    @Override
    public void releaseResources() {

    }
}
