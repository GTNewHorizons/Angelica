package me.jellysquid.mods.sodium.client.render.chunk.compile;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.buffer.VertexData;
import me.jellysquid.mods.sodium.client.gl.util.BufferSlice;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.BakedChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelVertexTransformer;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkModelOffset;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.sodium.block_context.BlockContextHolder;
import net.coderbot.iris.sodium.block_context.ChunkBuildBuffersExt;
import net.coderbot.iris.sodium.block_context.ContextAwareVertexWriter;
import net.minecraft.block.Block;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public class ChunkBuildBuffers implements ChunkBuildBuffersExt {
    private final ChunkModelBuffers[] delegates;
    private final VertexBufferBuilder[][] buffersByLayer;
    @Getter
    private final ChunkVertexType vertexType;

    private final ChunkModelOffset offset;

    private BlockContextHolder iris$contextHolder;

    public ChunkBuildBuffers(ChunkVertexType vertexType) {
        this.vertexType = vertexType;

        this.delegates = new ChunkModelBuffers[BlockRenderPass.COUNT];
        this.buffersByLayer = new VertexBufferBuilder[BlockRenderPass.COUNT][ModelQuadFacing.COUNT];

        this.offset = new ChunkModelOffset();

        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            final int passId = pass.ordinal();

            final VertexBufferBuilder[] buffers = this.buffersByLayer[passId];

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                buffers[facing.ordinal()] = new VertexBufferBuilder(vertexType.getBufferVertexFormat(), pass.bufferSize() / ModelQuadFacing.COUNT);
            }
        }

        if(AngelicaConfig.enableIris) {
            final Object2IntMap<Block> blockMatches = BlockRenderingSettings.INSTANCE.getBlockMatches();

            if (blockMatches != null) {
                this.iris$contextHolder = new BlockContextHolder(blockMatches);
            } else {
                this.iris$contextHolder = new BlockContextHolder();
            }
        }
    }

    public void init(ChunkRenderData.Builder renderData) {
        for (int i = 0; i < this.buffersByLayer.length; i++) {
            ChunkModelVertexTransformer[] writers = new ChunkModelVertexTransformer[ModelQuadFacing.COUNT];

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                final ModelVertexSink sink = this.vertexType.createBufferWriter(this.buffersByLayer[i][facing.ordinal()], SodiumClientMod.isDirectMemoryAccessEnabled());
                if (AngelicaConfig.enableIris && sink instanceof ContextAwareVertexWriter) {
                    ((ContextAwareVertexWriter) sink).iris$setContextHolder(iris$contextHolder);
                }
                writers[facing.ordinal()] = new ChunkModelVertexTransformer(sink, this.offset);
            }

            this.delegates[i] = new BakedChunkModelBuffers(writers, renderData);
        }
    }

    /**
     * Return the {@link ChunkModelVertexTransformer} for the given {@link BlockRenderPass} as mapped by the
     * {@link BlockRenderPassManager} for this render context.
     */
    public ChunkModelBuffers get(BlockRenderPass pass) {
        return this.delegates[pass.ordinal()];
    }

    /**
     * Creates immutable baked chunk meshes from all non-empty scratch buffers and resets the state of all mesh
     * builders. This is used after all blocks have been rendered to pass the finished meshes over to the graphics card.
     */
    public ChunkMeshData createMesh(BlockRenderPass pass, float x, float y, float z, boolean sortTranslucent) {
        VertexBufferBuilder[] builders = this.buffersByLayer[pass.ordinal()];

        ChunkMeshData meshData = null;
        int bufferLen = 0;

        for (int facingId = 0; facingId < builders.length; facingId++) {
            VertexBufferBuilder builder = builders[facingId];

            if (builder == null || builder.isEmpty()) {
                continue;
            }

            int start = bufferLen;
            int size = builder.getSize();

            if(meshData == null) {
                meshData = new ChunkMeshData();
            }

            meshData.setModelSlice(ModelQuadFacing.VALUES[facingId], new BufferSlice(start, size));

            bufferLen += size;
        }

        if (bufferLen <= 0) {
            return null;
        }

        ByteBuffer buffer = BufferUtils.createByteBuffer(bufferLen);

        for (Map.Entry<ModelQuadFacing, BufferSlice> entry : meshData.getSlices()) {
            BufferSlice slice = entry.getValue();
            buffer.position(slice.start);

            VertexBufferBuilder builder = this.buffersByLayer[pass.ordinal()][entry.getKey().ordinal()];
            builder.copyInto(buffer);
        }

        buffer.flip();

        if (sortTranslucent && pass.isTranslucent()) {
            ChunkBufferSorter.sortStandardFormat(vertexType, buffer, bufferLen, x, y, z);
        }

        meshData.setVertexData(new VertexData(buffer, this.vertexType.getCustomVertexFormat()));

        return meshData;
    }

    public void setRenderOffset(int x, int y, int z) {
        this.offset.set(x, y, z);
    }

    // Iris Compat
    public void iris$setLocalPos(int localPosX, int localPosY, int localPosZ) {
        if(!AngelicaConfig.enableIris) return;
        this.iris$contextHolder.setLocalPos(localPosX, localPosY, localPosZ);
    }

    public void iris$setMaterialId(Block block, short renderType) {
        if(!AngelicaConfig.enableIris) return;
        this.iris$contextHolder.set(block, renderType);
    }

    public void iris$resetBlockContext() {
        if(!AngelicaConfig.enableIris) return;
        this.iris$contextHolder.reset();
    }
}
