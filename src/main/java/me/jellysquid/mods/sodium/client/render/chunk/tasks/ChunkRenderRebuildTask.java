package me.jellysquid.mods.sodium.client.render.chunk.tasks;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.ChunkOcclusionDataBuilder;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.rendering.AngelicaBlockSafetyRegistry;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
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
import me.jellysquid.mods.sodium.client.util.MathUtil;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import me.jellysquid.mods.sodium.common.config.SodiumConfig;
import net.coderbot.iris.vertices.ExtendedDataHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.fluids.IFluidBlock;
import org.joml.Vector3d;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

    private Vector3d camera;

    private final boolean translucencySorting;

    public ChunkRenderRebuildTask(ChunkRenderContainer<T> render, ChunkRenderContext context, BlockPos offset) {
        this.render = render;
        this.offset = offset;
        this.context = context;
        this.camera = new Vector3d();
        this.translucencySorting = SodiumClientMod.options().advanced.translucencySorting;

    }

    public ChunkRenderRebuildTask<T> withCameraPosition(Vector3d camera) {
        this.camera = camera;
        return this;
    }

    private boolean rendersOutsideBoundingBox(TileEntity entity, int baseX, int baseY, int baseZ) {
        AxisAlignedBB box = entity.getRenderBoundingBox();

        // Check if it's explictly infinite
        if(box == TileEntity.INFINITE_EXTENT_AABB)
            return true;

        // Check if it extends outside our minimums
        if(box.minX < baseX || box.minY < baseY || box.minZ < baseZ)
            return true;

        // Check if it extends outside our maximums
        if(box.maxX > (baseX + 16) || box.maxY > (baseY + 16) || box.maxZ > (baseZ + 16))
            return true;

        // So it's within the chunk
        return false;
    }

    private boolean rendersOffThread(Block block) {
        int type = block.getRenderType();
        return type < 42 && type != 22 && AngelicaBlockSafetyRegistry.canBlockRenderOffThread(block);
    }

    @Override
    public ChunkBuildResult<T> performBuild(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        // COMPATIBLITY NOTE: Oculus relies on the LVT of this method being unchanged, at least in 16.5
        ChunkRenderData.Builder renderData = new ChunkRenderData.Builder();
        ChunkOcclusionDataBuilder occluder = new ChunkOcclusionDataBuilder();
        ChunkRenderBounds.Builder bounds = new ChunkRenderBounds.Builder();

        buffers.init(renderData);

        cache.init(this.context);

        final WorldSlice slice = cache.getWorldSlice();
        final RenderBlocks renderBlocks = new RenderBlocks(slice);

        int baseX = this.render.getOriginX();
        int baseY = this.render.getOriginY();
        int baseZ = this.render.getOriginZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos renderOffset = this.offset;
        final Tessellator tessellator = TessellatorManager.get();

        boolean hasMainThreadBlocks = false;

        for (int relY = 0; relY < 16; relY++) {
            if (cancellationSource.isCancelled()) {
                return null;
            }

            for (int relZ = 0; relZ < 16; relZ++) {
                for (int relX = 0; relX < 16; relX++) {
                    Block block = slice.getBlockRelative(relX + 16, relY + 16, relZ + 16);

                    // If the block is vanilla air, assume it renders nothing. Don't use isAir because mods
                    // can abuse it for all sorts of things
                    if (block.getMaterial() == Material.air) {
                        continue;
                    }

                    int meta = slice.getBlockMetadataRelative(relX + 16, relY + 16, relZ + 16);

                    pos.set(baseX + relX, baseY + relY, baseZ + relZ);
                    buffers.setRenderOffset(pos.x - renderOffset.getX(), pos.y - renderOffset.getY(), pos.z - renderOffset.getZ());

                    if(AngelicaConfig.enableIris) buffers.iris$setLocalPos(relX, relY, relZ);

                    if (rendersOffThread(block)) {
                        // Do regular block rendering
                        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
                            if (block.canRenderInPass(pass.ordinal()) && (!AngelicaConfig.enableSodiumFluidRendering || !(block instanceof IFluidBlock))) {
                                long seed = MathUtil.hashPos(pos.x, pos.y, pos.z);
                                if(AngelicaConfig.enableIris) buffers.iris$setMaterialId(block, ExtendedDataHelper.BLOCK_RENDER_TYPE);

                                if (cache.getBlockRenderer().renderModel(cache.getWorldSlice(), tessellator, renderBlocks, block, meta, pos, buffers.get(pass), true, seed)) {
                                    bounds.addBlock(relX, relY, relZ);
                                }
                            }
                        }
                    } else {
                        hasMainThreadBlocks = true;
                    }

                    // Do fluid rendering without RenderBlocks
                    if (AngelicaConfig.enableSodiumFluidRendering && block instanceof IFluidBlock) {
                        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
                            if (block.canRenderInPass(pass.ordinal())) {
                                if(AngelicaConfig.enableIris)  buffers.iris$setMaterialId(block, ExtendedDataHelper.FLUID_RENDER_TYPE);

                                if (cache.getFluidRenderer().render(slice, cache.getWorldSlice(), block, pos, buffers.get(pass))) {
                                    bounds.addBlock(relX, relY, relZ);
                                }
                            }
                        }
                    }

                    if(AngelicaConfig.enableIris) buffers.iris$resetBlockContext();

                    if (block.hasTileEntity(meta)) {
                        TileEntity entity = slice.getTileEntity(pos.x, pos.y, pos.z);

                        final TileEntitySpecialRenderer renderer = TileEntityRendererDispatcher.instance.getSpecialRenderer(entity);
                        if (entity != null && renderer != null) {
                            renderData.addTileEntity(entity, !rendersOutsideBoundingBox(entity, baseX, baseY, baseZ));
                            bounds.addBlock(relX, relY, relZ);
                        }
                    }

                    if (block.isOpaqueCube()) {
                        occluder.markClosed(pos);
                    }
                }
            }
        }

        if(hasMainThreadBlocks) {
            // Render the other blocks on the main thread
            try {
                CompletableFuture.runAsync(() -> this.performMainBuild(cache, buffers, cancellationSource, bounds), AngelicaRenderQueue.executor()).get();
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch(ExecutionException e) {
                throw new RuntimeException(e);
            }
            // Check if cancellation happened during that, so we don't render an incomplete chunk
            if(cancellationSource.isCancelled()) return null;
        }

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

    /**
     * Render the blocks that should be rendered on the main thread.
     *
     * TODO: Deduplicate this with the main method above.
     */
    private void performMainBuild(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource, ChunkRenderBounds.Builder bounds) {
        WorldSlice slice = cache.getWorldSlice();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int baseX = this.render.getOriginX();
        int baseY = this.render.getOriginY();
        int baseZ = this.render.getOriginZ();
        BlockPos renderOffset = this.offset;
        RenderBlocks rb = new RenderBlocks(slice.getWorld());
        for (int relY = 0; relY < 16; relY++) {
            if (cancellationSource.isCancelled()) {
                return;
            }
            for (int relZ = 0; relZ < 16; relZ++) {
                for (int relX = 0; relX < 16; relX++) {
                    Block block = slice.getBlockRelative(relX + 16, relY + 16, relZ + 16);

                    // Only render blocks that need main thread assistance
                    if (block.getMaterial() == Material.air || rendersOffThread(block)) {
                        continue;
                    }

                    // TODO: Collect data on which render types are hitting this code path most often
                    // so mods can be updated slowly

                    int meta = slice.getBlockMetadataRelative(relX + 16, relY + 16, relZ + 16);

                    pos.set(baseX + relX, baseY + relY, baseZ + relZ);
                    buffers.setRenderOffset(pos.x - renderOffset.getX(), pos.y - renderOffset.getY(), pos.z - renderOffset.getZ());
                    if(AngelicaConfig.enableIris) buffers.iris$setLocalPos(relX, relY, relZ);

                    // Do regular block rendering
                    for (BlockRenderPass pass : BlockRenderPass.VALUES) {
                        if (block.canRenderInPass(pass.ordinal()) && (!AngelicaConfig.enableSodiumFluidRendering || !(block instanceof IFluidBlock))) {
                            long seed = MathUtil.hashPos(pos.x, pos.y, pos.z);
                            if(AngelicaConfig.enableIris) buffers.iris$setMaterialId(block, ExtendedDataHelper.BLOCK_RENDER_TYPE);

                            if (cache.getBlockRenderer().renderModel(slice.getWorld(), Tessellator.instance, rb, block, meta, pos, buffers.get(pass), true, seed)) {
                                bounds.addBlock(relX, relY, relZ);
                            }
                        }
                    }

                    if(AngelicaConfig.enableIris) buffers.iris$resetBlockContext();
                }
            }
        }
    }

    @Override
    public void releaseResources() {
        this.context.releaseResources();
    }
}
