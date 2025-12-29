package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.ChunkTaskProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.ChunkTaskRegistry;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSectionCache;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.lists.SectionTicker;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizons.angelica.mixins.interfaces.RenderSectionManagerAccessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AngelicaRenderSectionManager extends RenderSectionManager {
    private boolean initialCameraSectionReady = false;

    private final WorldClient world;
    private final ClonedChunkSectionCache sectionCache;
    private final ChunkTaskProvider taskProvider;

    public AngelicaRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world, int renderDistance, CommandList commandList, int minSection, int maxSection, int requestedThreads, ChunkTaskProvider taskProvider) {
        super(configuration, () -> new AngelicaChunkBuildContext(configuration, world), AngelicaChunkRenderer::new, renderDistance, commandList, minSection, maxSection, requestedThreads, true  /* hasShadowPass = true for Iris */);
        this.world = world;
        this.sectionCache = new ClonedChunkSectionCache(world);
        this.taskProvider = taskProvider;
    }

    public static AngelicaRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList) {
        final ChunkTaskProvider provider = ChunkTaskRegistry.getActiveProvider();
        return new AngelicaRenderSectionManager(AngelicaRenderPassConfiguration.build(vertexType), world, renderDistance, commandList, 0, 16, provider.threadCount(), provider);
    }

    public void setCameraPosition(double x, double y, double z) {
        this.cameraPosition.set(x, y, z);
    }

    private boolean isChunkNotLoaded(int chunkX, int chunkZ) {
        return this.world.getChunkFromChunkCoords(chunkX, chunkZ).isEmpty();
    }

    @Override
    public void onSectionAdded(int x, int y, int z) {
        super.onSectionAdded(x, y, z);

        // If chunk isn't actually loaded (EmptyChunk placeholder), make sections opaque for main pass.
        // This prevents BFS from traversing through chunks we haven't received from the server.
        if (isChunkNotLoaded(x, z)) {
            renderListManager.updateVisibilityData(x, y, z, 0L);
        }
    }

    @Override
    public void update(Viewport positionedViewport, int frame, boolean spectator) {
        if (!isInShadowPass() && !initialCameraSectionReady) {
            var origin = positionedViewport.getChunkCoord();
            long key = PositionUtil.packSection(origin.x(), origin.y(), origin.z());
            if (!((RenderSectionManagerAccessor) this).angelica$getSectionByPosition().containsKey(key)) {
                return;
            }
            initialCameraSectionReady = true;
        }
        super.update(positionedViewport, frame, spectator);
    }

    @Override
    protected AsyncOcclusionMode getAsyncOcclusionMode() {
        return AsyncOcclusionMode.EVERYTHING;
    }

    @Override
    protected boolean shouldRespectUpdateTaskQueueSizeLimit() {
        return true;
    }

    @Override
    protected boolean useFogOcclusion() {
        return true;
    }

    @Override
    protected boolean isDebugInfoShown() {
        return Minecraft.getMinecraft().gameSettings.showDebugInfo;
    }

    @Override
    protected boolean shouldUseOcclusionCulling(Viewport positionedViewport, boolean spectator) {
        if (!spectator) return true;

        final var camBlockPos = positionedViewport.getBlockCoord();
        return !this.world.getBlock(camBlockPos.x(), camBlockPos.y(), camBlockPos.z()).isOpaqueCube();
    }

    @Override
    protected boolean isSectionVisuallyEmpty(int x, int y, int z) {
        final Chunk chunk = this.world.getChunkFromChunkCoords(x, z);
        if (chunk.isEmpty()) {
            return true;
        }
        final var array = chunk.getBlockStorageArray();
        if (y < 0 || y >= array.length) {
            return true;
        }
        return array[y] == null || array[y].isEmpty();
    }

    @Override
    protected @Nullable ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame) {
        if (isSectionVisuallyEmpty(render.getChunkX(), render.getChunkY(), render.getChunkZ())) {
            return null;
        }

        return this.taskProvider.createRebuildTask(render, frame, this.cameraPosition, this.sectionCache);
    }

    @Override
    protected void invalidateCachedSectionData(RenderSection section) {
        super.invalidateCachedSectionData(section);
        this.sectionCache.invalidate(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    @Override
    public void updateChunks(boolean updateImmediately) {
        this.sectionCache.cleanup();
        super.updateChunks(updateImmediately);
    }

    @Override
    protected boolean allowImportantRebuilds() {
        return !SodiumClientMod.options().performance.alwaysDeferChunkUpdates;
    }

    @Override
    protected @Nullable SectionTicker createSectionTicker() {
        return new GenericSectionSpriteTicker<>(AngelicaRenderSectionManager::markSpriteActive);
    }

    private static void markSpriteActive(TextureAtlasSprite sprite) {
        ((SpriteExtension) sprite).celeritas$markActive();
    }

    @Override
    public boolean isInShadowPass() {
        return IrisShaderProviderHolder.isShadowPass();
    }

    @Override
    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>(super.getDebugStrings());

        // Add thread info
        var builder = getBuilder();
        int busyThreads = builder.getBusyThreadCount();
        int totalThreads = builder.getTotalThreadCount();

        if (totalThreads > 0) {
            list.add(String.format("Chunk Workers: %d/%d busy", busyThreads, totalThreads));
        } else {
            list.add("Chunk Workers: single-threaded");
        }

        // Main thread queue metrics
        final int queueDepth = AngelicaRenderQueue.getQueueDepth();
        final int tasksRan = AngelicaRenderQueue.getLastFrameTasksRan();
        final double mtTimeMs = AngelicaRenderQueue.getLastFrameTimeNs() / 1_000_000.0;
        list.add(String.format("MT Queue: %d, ran %d (%.1fms)", queueDepth, tasksRan, mtTimeMs));

        return list;
    }

    @Override
    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, CameraTransform occlusionCamera, CameraTransform camera) {
        // Shadow pass graph update is async - must wait for it to complete before rendering
        if (IrisShaderProviderHolder.isShadowPass()) {
            finishAllGraphUpdates();
        }
        super.renderLayer(matrices, pass, occlusionCamera, camera);
    }
}
