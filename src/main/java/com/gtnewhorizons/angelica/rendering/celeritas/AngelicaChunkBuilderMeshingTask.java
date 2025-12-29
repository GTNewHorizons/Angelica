package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.BlockRenderContext;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.ContextAwareChunkVertexEncoder;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import net.coderbot.iris.vertices.ExtendedDataHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionVisibilityBuilder;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public abstract class AngelicaChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    protected final RenderSection render;
    protected final int buildTime;
    protected final Vector3d camera;

    protected record DeferredBlock(int x, int y, int z, Block block, int meta, int pass) {}

    public AngelicaChunkBuilderMeshingTask(RenderSection render, int time, Vector3d camera) {
        this.render = render;
        this.buildTime = time;
        this.camera = camera;
    }

    protected abstract Tessellator getTessellator();
    protected abstract void setRenderPass(int pass);
    protected abstract IBlockAccess getBlockAccess();
    protected abstract SmoothBiomeColorCache getBiomeColorCache();

    protected boolean isThreaded() {
        return false;
    }

    protected boolean canRenderOffThread(Block block) {
        return false;
    }

    protected void onEnterExecute() {}

    protected void onExitExecute() {}

    protected void addExtraCrashInfo(CrashReportCategory category) {}

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        final AngelicaChunkBuildContext buildContext = (AngelicaChunkBuildContext) context;
        final MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> renderData = new MinecraftBuiltRenderSectionData<>();
        final SectionVisibilityBuilder occluder = new SectionVisibilityBuilder();

        final ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.render.getSectionIndex());

        final int minX = this.render.getOriginX();
        final int minY = this.render.getOriginY();
        final int minZ = this.render.getOriginZ();

        final int maxX = minX + 16;
        final int maxY = minY + 16;
        final int maxZ = minZ + 16;

        final BlockPos blockPos = new BlockPos(minX, minY, minZ);
        final IBlockAccess region = getBlockAccess();
        final SmoothBiomeColorCache biomeColorCache = getBiomeColorCache();

        onEnterExecute();

        final Tessellator tessellator = getTessellator();

        try {
            tessellator.setTranslation(-minX, -minY, -minZ);
            SmoothBiomeColorCache.setActiveCache(biomeColorCache);

            final IrisShaderProvider provider = IrisShaderProviderHolder.getProvider();
            final Map<Block, RenderLayer> blockTypeIds = provider != null ? provider.getBlockTypeIds() : null;
            final BlockRenderContext blockRenderContext = buildContext.getBlockRenderContext();

            final boolean threaded = isThreaded();
            final List<DeferredBlock> deferredBlocks = threaded ? new ArrayList<>() : null;

            final RenderBlocks renderBlocks = new RenderBlocks(region);

            for (int y = minY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    return null;
                }

                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        blockPos.set(x, y, z);

                        final Block block = region.getBlock(x, y, z);

                        if (block == Blocks.air) {
                            continue;
                        }

                        final int meta = region.getBlockMetadata(x, y, z);

                        if (block.hasTileEntity(meta)) {
                            final TileEntity tileEntity = region.getTileEntity(x, y, z);
                            if (TileEntityRendererDispatcher.instance.hasSpecialRenderer(tileEntity)) {
                                renderData.globalBlockEntities.add(tileEntity);
                            }
                        }

                        final boolean canRenderOffThread = !threaded || canRenderOffThread(block);

                        for (int pass = 0; pass < AngelicaChunkBuildContext.NUM_PASSES; pass++) {
                            boolean canRender = block.canRenderInPass(pass);

                            // Shader pack can override render layer via block.properties
                            if (blockTypeIds != null) {
                                final RenderLayer overrideLayer = blockTypeIds.get(block);
                                if (overrideLayer != null) {
                                    canRender = switch (pass) {
                                        case 0 -> overrideLayer == RenderLayer.cutout();
                                        case 1 -> overrideLayer == RenderLayer.translucent();
                                        default -> canRender;
                                    };
                                }
                            }

                            if (canRender) {
                                if (!canRenderOffThread) {
                                    deferredBlocks.add(new DeferredBlock(x, y, z, block, meta, pass));
                                    continue;
                                }

                                renderBlock(block, x, y, z, pass, tessellator, renderBlocks,
                                    buffers, buildContext, blockRenderContext, minX, minY, minZ);
                            }
                        }

                        if (block.isOpaqueCube()) {
                            occluder.markOpaque(x, y, z);
                        }
                    }
                }
            }

            // Process deferred blocks on main thread if any
            if (deferredBlocks != null && !deferredBlocks.isEmpty()) {
                processDeferredBlocks(deferredBlocks, buildContext, buffers, region, minX, minY, minZ);
            }

            final Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes =
                BuiltSectionMeshParts.groupFromBuildBuffers(buffers,
                    (float) camera.x - minX, (float) camera.y - minY, (float) camera.z - minZ);

            if (!meshes.isEmpty()) {
                renderData.hasBlockGeometry = true;
            }

            renderData.visibilityData = occluder.computeVisibilityEncoding();

            SmoothBiomeColorCache.clearActiveCache();
            tessellator.setTranslation(0, 0, 0);

            CeleritasDebug.incrementChunkUpdateCounter();

            return new ChunkBuildOutput(this.render, renderData, meshes, this.buildTime);

        } catch (ReportedException ex) {
            throw fillCrashInfo(ex.getCrashReport(), region, blockPos);
        } catch (Throwable ex) {
            throw fillCrashInfo(CrashReport.makeCrashReport(ex, "Encountered exception while building chunk meshes"), region, blockPos);
        } finally {
            SmoothBiomeColorCache.clearActiveCache();
            onExitExecute();
        }
    }

    protected void renderBlock(Block block, int x, int y, int z, int pass, Tessellator tessellator, RenderBlocks renderBlocks, ChunkBuildBuffers buffers, AngelicaChunkBuildContext buildContext, BlockRenderContext blockRenderContext, int originX, int originY, int originZ) {

        final Material blockMaterial = block.getMaterial();
        final boolean isFluid = blockMaterial == Material.water || blockMaterial == Material.lava;

        // Lava is opaque - use solid to skip unnecessary alpha testing
        final var passMaterial = blockMaterial == Material.lava ? AngelicaRenderPassConfiguration.SOLID_MATERIAL : buffers.getRenderPassConfiguration().getMaterialForRenderType(BlockRenderLayer.fromVanillaPass(pass));

        final var encoder = buffers.get(passMaterial).getEncoder();
        final ContextAwareChunkVertexEncoder contextEncoder = (encoder instanceof ContextAwareChunkVertexEncoder) ? (ContextAwareChunkVertexEncoder) encoder : null;

        if (contextEncoder != null) {
            blockRenderContext.localPosX = x & 15;
            blockRenderContext.localPosY = y & 15;
            blockRenderContext.localPosZ = z & 15;
            final byte lightValue = (byte) block.getLightValue();
            if (isFluid) {
                contextEncoder.prepareToRenderFluid(blockRenderContext, block, lightValue);
            } else {
                contextEncoder.prepareToRenderBlock(blockRenderContext, block,
                    ExtendedDataHelper.BLOCK_RENDER_TYPE, lightValue);
            }
        }

        setRenderPass(pass);
        tessellator.startDrawingQuads();
        renderBlocks.renderBlockByRenderType(block, x, y, z);
        buildContext.copyRawBuffer(tessellator.rawBuffer, tessellator.vertexCount, buffers,
            passMaterial, originX, originY, originZ);
        tessellator.reset();
        tessellator.isDrawing = false;

        if (contextEncoder != null) {
            contextEncoder.finishRenderingBlock();
        }
    }

    private void processDeferredBlocks(List<DeferredBlock> deferredBlocks, AngelicaChunkBuildContext buildContext, ChunkBuildBuffers buffers, IBlockAccess region, int minX, int minY, int minZ) {

        final BlockRenderContext blockRenderContext = buildContext.getBlockRenderContext();

        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            final RenderBlocks mainThreadRenderBlocks = new RenderBlocks(region);
            final Tessellator mainTessellator = Tessellator.instance;
            mainTessellator.setTranslation(-minX, -minY, -minZ);

            for (DeferredBlock deferred : deferredBlocks) {
                renderBlock(deferred.block(), deferred.x(), deferred.y(), deferred.z(), deferred.pass(),
                    mainTessellator, mainThreadRenderBlocks, buffers, buildContext,
                    blockRenderContext, minX, minY, minZ);
            }

            mainTessellator.setTranslation(0, 0, 0);
        }, AngelicaRenderQueue.executor());

        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during deferred block rendering", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error during main thread deferred block rendering", e);
        }
    }

    private ReportedException fillCrashInfo(CrashReport report, IBlockAccess slice, BlockPos pos) {
        final CrashReportCategory crashReportSection = report.makeCategory("Block being rendered");

        Block block = Blocks.air;
        int meta = 0;
        try {
            block = slice.getBlock(pos.x, pos.y, pos.z);
            meta = slice.getBlockMetadata(pos.x, pos.y, pos.z);
        } catch (Exception ignored) {}

        crashReportSection.addCrashSection("Block", block);
        crashReportSection.addCrashSection("Block location", String.format("World: (%d,%d,%d)", pos.x, pos.y, pos.z));
        crashReportSection.addCrashSection("Block meta", meta);
        crashReportSection.addCrashSection("Chunk section", this.render);

        addExtraCrashInfo(crashReportSection);

        return new ReportedException(report);
    }
}
