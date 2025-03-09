package org.embeddedt.embeddium.impl.render.chunk.compile.buffers;

import org.embeddedt.embeddium.impl.common.datastructure.ContextBundle;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;

import java.util.Objects;

public class BakedChunkModelBuilder implements ChunkModelBuilder {
    private final ChunkMeshBufferBuilder[] vertexBuffers;
    private final boolean splitBySide;

    private ContextBundle<RenderSection> renderData;

    public BakedChunkModelBuilder(ChunkVertexEncoder encoder, int stride, TerrainRenderPass pass) {
        var vertexBuffers = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            vertexBuffers[facing] = new ChunkMeshBufferBuilder(encoder, stride, 64 * 1024, pass.isSorted() && facing == ModelQuadFacing.UNASSIGNED.ordinal());
        }

        this.vertexBuffers = vertexBuffers;
        this.splitBySide = !pass.isSorted();
    }

    @Override
    public ChunkMeshBufferBuilder getVertexBuffer(ModelQuadFacing facing) {
        Objects.requireNonNull(this.renderData, "Builder has not been started");
        return splitBySide ? this.vertexBuffers[facing.ordinal()] : this.vertexBuffers[ModelQuadFacing.UNASSIGNED.ordinal()];
    }

    @Override
    public ContextBundle<RenderSection> getSectionContextBundle() {
        return this.renderData;
    }

    public void destroy() {
        for (ChunkMeshBufferBuilder builder : this.vertexBuffers) {
            if(builder != null) {
                builder.destroy();
            }
        }
    }

    public void begin(ContextBundle<RenderSection> renderData, int sectionIndex) {
        this.renderData = renderData;

        for (var vertexBuffer : this.vertexBuffers) {
            if(vertexBuffer != null) {
                vertexBuffer.start(sectionIndex);
            }
        }
    }

    public boolean isEmpty() {
        for (var vertexBuffer : this.vertexBuffers) {
            if (vertexBuffer != null && !vertexBuffer.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
