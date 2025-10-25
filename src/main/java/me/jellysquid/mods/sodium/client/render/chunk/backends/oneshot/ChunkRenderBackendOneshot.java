package me.jellysquid.mods.sodium.client.render.chunk.backends.oneshot;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.DrawCommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.util.BufferSlice;
import me.jellysquid.mods.sodium.client.gl.util.GlMultiDrawBatch;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkRenderShaderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import org.lwjgl.opengl.GL20;

import java.util.Iterator;

public class ChunkRenderBackendOneshot extends ChunkRenderShaderBackend<ChunkOneshotGraphicsState> {
    private final GlMultiDrawBatch batch = new GlMultiDrawBatch(ModelQuadFacing.COUNT);

    public ChunkRenderBackendOneshot(ChunkVertexType vertexType) {
        super(vertexType);
    }

    @Override
    public void upload(CommandList commandList, Iterator<ChunkBuildResult<ChunkOneshotGraphicsState>> queue) {
        while (queue.hasNext()) {
            ChunkBuildResult<ChunkOneshotGraphicsState> result = queue.next();

            ChunkRenderContainer<ChunkOneshotGraphicsState> render = result.render;
            ChunkRenderData data = result.data;

            for (BlockRenderPass pass : result.passesToUpload) {
                ChunkOneshotGraphicsState state = render.getGraphicsState(pass);
                ChunkMeshData mesh = data.getMesh(pass);

                if (mesh.hasVertexData()) {
                    if (state == null) {
                        state = new ChunkOneshotGraphicsState(RenderDevice.INSTANCE, render);
                    }

                    state.upload(commandList, mesh);
                    // For code simplicity, ChunkOneshotGraphicsState stores the buffer unconditionally
                    // Reset it here if the pass isn't translucent, as we don't want to store useless
                    // buffers
                    if(!pass.isTranslucent())
                        state.setTranslucencyData(null);
                } else {
                    if (state != null) {
                        state.delete(commandList);
                    }

                    state = null;
                }

                render.setGraphicsState(pass, state);
            }

            render.setData(data);
        }
    }

    @Override
    public void render(CommandList commandList, ChunkRenderListIterator<ChunkOneshotGraphicsState> it, ChunkCameraContext camera) {
        while (it.hasNext()) {
            ChunkOneshotGraphicsState state = it.getGraphicsState();
            int visibleFaces = it.getVisibleFaces();

            this.buildBatch(state, visibleFaces);

            if (this.batch.isBuilding()) {
                this.prepareDrawBatch(camera, state);
                this.drawBatch(commandList, state);
            }

            it.advance();
        }
    }

    protected void prepareDrawBatch(ChunkCameraContext camera, ChunkOneshotGraphicsState state) {
        final float modelX = camera.getChunkModelOffset(state.getX(), camera.blockOriginX, camera.originX);
        final float modelY = camera.getChunkModelOffset(state.getY(), camera.blockOriginY, camera.originY);
        final float modelZ = camera.getChunkModelOffset(state.getZ(), camera.blockOriginZ, camera.originZ);

        GL20.glVertexAttrib4f(ChunkShaderBindingPoints.MODEL_OFFSET.getGenericAttributeIndex(), modelX, modelY, modelZ, 0.0F);
    }

    protected void buildBatch(ChunkOneshotGraphicsState state, int visibleFaces) {
        GlMultiDrawBatch batch = this.batch;
        batch.begin();

        for (int i = 0; i < ModelQuadFacing.COUNT; i++) {
            if ((visibleFaces & (1 << i)) == 0) {
                continue;
            }

            long part = state.getModelPart(i);
            batch.addChunkRender(BufferSlice.unpackStart(part), BufferSlice.unpackLength(part));
        }
    }

    protected void drawBatch(CommandList commandList, ChunkOneshotGraphicsState state) {
        this.batch.end();

        if (!batch.isEmpty()) {
	        try (DrawCommandList drawCommandList = commandList.beginTessellating(state.tessellation)) {
	            drawCommandList.multiDrawArrays(this.batch.getIndicesBuffer(), this.batch.getLengthBuffer());
	        }
        }
    }

    @Override
    public void delete() {
        super.delete();

        this.batch.delete();
    }

    @Override
    public Class<ChunkOneshotGraphicsState> getGraphicsStateType() {
        return ChunkOneshotGraphicsState.class;
    }

    @Override
    public String getRendererName() {
        return "Oneshot";
    }
}
