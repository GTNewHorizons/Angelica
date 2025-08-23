package org.taumc.celeritas.impl.render.terrain.compile.task;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionVisibilityBuilder;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3d;
import org.taumc.celeritas.impl.extensions.TessellatorExtension;
import org.taumc.celeritas.impl.extensions.WorldClientExtension;
import org.taumc.celeritas.impl.render.terrain.compile.ArchaicChunkBuildContext;
import org.taumc.celeritas.impl.world.biome.SmoothBiomeColorCache;
import org.taumc.celeritas.impl.world.cloned.ChunkRenderContext;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    private final RenderSection render;
    private final int buildTime;
    private final Vector3d camera;
    private final ChunkRenderContext renderContext;

    private static final MethodHandle RENDER_PASS_HANDLE;

    static {
        try {
            Field field = ForgeHooksClient.class.getDeclaredField("worldRenderPass");
            field.setAccessible(true);
            RENDER_PASS_HANDLE = MethodHandles.publicLookup().unreflectSetter(field);
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setForgeRenderPass(int pass) {
        try {
            RENDER_PASS_HANDLE.invokeExact(pass);
        } catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public ChunkBuilderMeshingTask(RenderSection render, ChunkRenderContext context, int time, Vector3d camera) {
        this.render = render;
        this.buildTime = time;
        this.camera = camera;
        this.renderContext = context;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        ArchaicChunkBuildContext buildContext = (ArchaicChunkBuildContext)context;
        MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity> renderData = new MinecraftBuiltRenderSectionData<>();
        SectionVisibilityBuilder occluder = new SectionVisibilityBuilder();

        ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.render.getSectionIndex());

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        // Initialise with minX/minY/minZ so initial getBlockState crash context is correct
        BlockPos blockPos = new BlockPos(minX, minY, minZ);

        var world = Minecraft.getMinecraft().theWorld;
        ((WorldClientExtension)world).celeritas$getSmoothBiomeColorCache().update(new SectionPos(this.render.getChunkX(), this.render.getChunkY(), this.render.getChunkZ()));
        var chunk = world.getChunkFromChunkCoords(this.render.getChunkX(), this.render.getChunkZ());
        var section = chunk.getBlockStorageArray()[this.render.getChunkY()];
        var region = new ChunkCache(world, minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1, 1);
        var renderBlocks = new RenderBlocks(region);
        var tesselator = Tessellator.instance;
        var extTesselator = (TessellatorExtension)tesselator;

        tesselator.setTranslation(-this.render.getOriginX(), -this.render.getOriginY(), -this.render.getOriginZ());

        SmoothBiomeColorCache.enabled = true;

        try {
            for (int y = minY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    return null;
                }

                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        blockPos.set(x, y, z);

                        var block = section.getBlockByExtId(x & 15, y & 15, z & 15);

                        if (block == Blocks.air) {
                            continue;
                        }

                        if (block.hasTileEntity(section.getExtBlockMetadata(x & 15, y & 15, z & 15))) {
                            TileEntity tileEntity = chunk.func_150806_e/*getBlockTileEntityInChunk*/(x & 15, y, z & 15);
                            if (TileEntityRendererDispatcher.instance.hasSpecialRenderer(tileEntity)) {
                                renderData.globalBlockEntities.add(tileEntity);
                            }
                        }

                        for (int pass = 0; pass < ArchaicChunkBuildContext.NUM_PASSES; pass++) {
                            if (block.canRenderInPass(pass)) {
                                setForgeRenderPass(pass);
                                tesselator.startDrawingQuads();
                                renderBlocks.renderBlockByRenderType(block, x, y, z);
                                buildContext.copyRawBuffer(extTesselator.celeritas$getRawBuffer(), extTesselator.celeritas$getVertexCount(), buffers, buffers.getRenderPassConfiguration().getMaterialForRenderType(pass));
                                extTesselator.celeritas$reset();
                            }
                        }



                        if (block.isOpaqueCube()) {
                            occluder.markOpaque(x, y, z);
                        }
                    }
                }
            }
        } catch (ReportedException ex) {
            // Propagate existing crashes (add context)
            throw fillCrashInfo(ex.getCrashReport(), world, blockPos);
        } catch (Throwable ex) {
            // Create a new crash report for other exceptions (e.g. thrown in getQuads)
            throw fillCrashInfo(CrashReport.makeCrashReport(ex, "Encountered exception while building chunk meshes"), world, blockPos);
        } finally {
            SmoothBiomeColorCache.enabled = false;
            tesselator.setTranslation(0, 0, 0);
        }


        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes = BuiltSectionMeshParts.groupFromBuildBuffers(buffers,(float)camera.x - minX, (float)camera.y - minY, (float)camera.z - minZ);

        if (!meshes.isEmpty()) {
            renderData.hasBlockGeometry = true;
        }

        renderData.visibilityData = occluder.computeVisibilityEncoding();

        return new ChunkBuildOutput(this.render, renderData, meshes, this.buildTime);
    }

    private ReportedException fillCrashInfo(CrashReport report, IBlockAccess slice, BlockPos pos) {
        CrashReportCategory crashReportSection = report.makeCategory("Block being rendered");

        Block state = Blocks.air;
        int meta = 0;
        try {
            state = slice.getBlock(pos.x, pos.y, pos.z);
            meta = slice.getBlockMetadata(pos.x, pos.y, pos.z);
        } catch (Exception ignored) {}
        CrashReportCategory.func_147153_a/*addBlockInfo*/(crashReportSection, pos.x, pos.y, pos.z, state, meta);

        crashReportSection.addCrashSection("Chunk section", this.render);
        /*
        if (this.renderContext != null) {
            crashReportSection.addCrashSection("Render context volume", this.renderContext.getVolume());
        }

         */

        return new ReportedException(report);
    }
}
