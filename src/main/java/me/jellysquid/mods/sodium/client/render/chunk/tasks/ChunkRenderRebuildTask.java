package me.jellysquid.mods.sodium.client.render.chunk.tasks;

import com.gtnewhorizons.angelica.compat.forge.EmptyModelData;
import com.gtnewhorizons.angelica.compat.forge.ForgeHooksClientExt;
import com.gtnewhorizons.angelica.compat.forge.IModelData;
import com.gtnewhorizons.angelica.compat.forge.ModelDataManager;
import com.gtnewhorizons.angelica.compat.mojang.BakedModel;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderType;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import com.gtnewhorizons.angelica.compat.mojang.ChunkOcclusionDataBuilder;
import com.gtnewhorizons.angelica.compat.mojang.ChunkPos;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.compat.mojang.FluidState;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayers;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import org.joml.Vector3d;

import java.util.Map;

/**
 * Rebuilds all the meshes of a chunk for each given render pass with non-occluded blocks. The result is then uploaded
 * to graphics memory on the main thread.
 *
 * This task takes a slice of the world from the thread it is created on. Since these slices require rather large
 * array allocations, they are pooled to ensure that the garbage collector doesn't become overloaded.
 */
public class ChunkRenderRebuildTask<T extends ChunkGraphicsState> extends ChunkRenderBuildTask<T> {
    private final ChunkRenderContainer<T> render;

    private final BlockPos offset;

    private final ChunkRenderContext context;

    private final Map<BlockPos, IModelData> modelDataMap;

    private Vector3d camera;

    private final boolean translucencySorting;

    public ChunkRenderRebuildTask(ChunkRenderContainer<T> render, ChunkRenderContext context, BlockPos offset) {
        this.render = render;
        this.offset = offset;
        this.context = context;
        this.camera = new Vector3d();
        this.translucencySorting = SodiumClientMod.options().advanced.translucencySorting;

        this.modelDataMap = ModelDataManager.getModelData(
            Minecraft.getMinecraft().theWorld,
            new ChunkPos(ChunkSectionPos.getSectionCoord(this.render.getOriginX()), ChunkSectionPos.getSectionCoord(this.render.getOriginZ())));
    }

    public ChunkRenderRebuildTask<T> withCameraPosition(Vector3d camera) {
        this.camera = camera;
        return this;
    }

    @Override
    public ChunkBuildResult<T> performBuild(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        // COMPATIBLITY NOTE: Oculus relies on the LVT of this method being unchanged, at least in 16.5
        ChunkRenderData.Builder renderData = new ChunkRenderData.Builder();
        ChunkOcclusionDataBuilder occluder = new ChunkOcclusionDataBuilder();
        ChunkRenderBounds.Builder bounds = new ChunkRenderBounds.Builder();

        buffers.init(renderData);

        cache.init(this.context);

        WorldSlice slice = cache.getWorldSlice();

        int baseX = this.render.getOriginX();
        int baseY = this.render.getOriginY();
        int baseZ = this.render.getOriginZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos renderOffset = this.offset;

        for (int relY = 0; relY < 16; relY++) {
            if (cancellationSource.isCancelled()) {
                return null;
            }

            for (int relZ = 0; relZ < 16; relZ++) {
                for (int relX = 0; relX < 16; relX++) {
                    BlockState blockState = slice.getBlockStateRelative(relX + 16, relY + 16, relZ + 16);

                    if (blockState.isAir()) {
                        continue;
                    }

                    // TODO: commit this separately
                    pos.set(baseX + relX, baseY + relY, baseZ + relZ);
                    buffers.setRenderOffset(pos.x - renderOffset.getX(), pos.y - renderOffset.getY(), pos.z - renderOffset.getZ());

                    if (blockState.getRenderType() == BlockRenderType.MODEL) {
                    for (RenderLayer layer : RenderLayer.getBlockLayers()) {
	                        if (!RenderLayers.canRenderInLayer(blockState, layer)) {
	                        	continue;
	                        }

	                        ForgeHooksClientExt.setRenderLayer(layer);
                            IModelData modelData = modelDataMap.getOrDefault(pos, EmptyModelData.INSTANCE);

	                        BakedModel model = cache.getBlockModels().getModel(blockState);

	                        long seed = blockState.getRenderingSeed(pos);

	                        if (cache.getBlockRenderer().renderModel(cache.getLocalSlice(), blockState, pos, model, buffers.get(layer), true, seed, modelData)) {
	                            bounds.addBlock(relX, relY, relZ);
	                        }

                        }
                    }

                    FluidState fluidState = blockState.getFluidState();

                    if (!fluidState.isEmpty()) {
                        for (RenderLayer layer : RenderLayer.getBlockLayers()) {
                            if (!RenderLayers.canRenderInLayer(fluidState, layer)) {
                                continue;
                            }

                            ForgeHooksClientExt.setRenderLayer(layer);

	                        if (cache.getFluidRenderer().render(cache.getLocalSlice(), fluidState, pos, buffers.get(layer))) {
	                            bounds.addBlock(relX, relY, relZ);
	                        }
                        }
                    }

                    if (blockState.hasTileEntity()) {
                        TileEntity entity = slice.getBlockEntity(pos);

                        if (entity != null) {
                            // TODO: Sodium TileEntity Rendering
//                            BlockEntityRenderer<TileEntity> renderer = BlockEntityRenderDispatcher.INSTANCE.get(entity);
//
//                            if (renderer != null) {
//                                bounds.addBlock(relX, relY, relZ);
//                            }
                        }
                    }

                    if (blockState.isOpaqueFullCube(slice, pos)) {
                        occluder.markClosed(pos);
                    }
                }
            }
        }

        ForgeHooksClientExt.setRenderLayer(null);

        render.setRebuildForTranslucents(false);
        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            ChunkMeshData mesh = buffers.createMesh(pass, (float)camera.x - offset.getX(), (float)camera.y - offset.getY(), (float)camera.z - offset.getZ(), this.translucencySorting);

            if (mesh != null) {
                renderData.setMesh(pass, mesh);
                if(this.translucencySorting && pass.isTranslucent())
                    render.setRebuildForTranslucents(true);
            }
        }

        renderData.setOcclusionData(occluder.build());
        renderData.setBounds(bounds.build(this.render.getChunkPos()));

        return new ChunkBuildResult<>(this.render, renderData.build());
    }

    @Override
    public void releaseResources() {
        this.context.releaseResources();
    }
}
