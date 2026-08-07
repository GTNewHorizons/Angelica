package com.gtnewhorizons.angelica.rendering.celeritas;

import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.cubicchunks.CubicChunksAPI;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.mixins.interfaces.RenderListManagerAccessor;
import com.gtnewhorizons.angelica.mixins.interfaces.RenderSectionManagerAccessor;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.ChunkTaskProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.ChunkTaskRegistry;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSectionCache;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.lists.SectionTicker;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public class AngelicaRenderSectionManager extends RenderSectionManager {
    private static final long P_MESH_REGIONS = Tracy.plotHandle("mesh.regions");
    private static final long P_MESH_VISIBLE_CHUNKS = Tracy.plotHandle("mesh.visibleChunks");
    private static final long P_SHADOW_REGIONS = Tracy.plotHandle("shadow.regions");
    private static final long P_SHADOW_VISIBLE_CHUNKS = Tracy.plotHandle("shadow.visibleChunks");
    private static final long P_MESH_SCHEDULED_JOBS = Tracy.plotHandle("mesh.scheduledJobs");
    private static final long P_MESH_BUSY_THREADS = Tracy.plotHandle("mesh.busyThreads");
    private static final long P_MESH_DEVICE_USED = Tracy.plotHandle("mesh.deviceUsed", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_MESH_DEVICE_ALLOCATED = Tracy.plotHandle("mesh.deviceAllocated", TracyBackend.PLOT_FORMAT_MEMORY);

    private static final Tracy.ZoneId Z_BIOME_REBUILDS = Tracy.zoneId("biomeRebuilds", Tracy.COLOR_TERRAIN);
    private static final Tracy.ZoneId Z_GRAPH_SEARCH = Tracy.zoneId("graphSearch", Tracy.COLOR_TERRAIN);
    private static final Tracy.ZoneId Z_SECTION_UPLOAD = Tracy.zoneId("sectionUpload", Tracy.COLOR_TERRAIN);
    private static final Tracy.ZoneId Z_SHADOW_GRAPH_WAIT = Tracy.zoneId("shadowGraphWait", Tracy.COLOR_TERRAIN);

    private boolean initialCameraSectionReady = false;

    private final WorldClient world;
    private final ClonedChunkSectionCache sectionCache;
    private final ChunkTaskProvider taskProvider;
    private final LongOpenHashSet biomeRebuildColumns = new LongOpenHashSet();

    public AngelicaRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world, int renderDistance, CommandList commandList, int minSection, int maxSection, int requestedThreads, ChunkTaskProvider taskProvider) {
        super(configuration, () -> new AngelicaChunkBuildContext(configuration, world), AngelicaChunkRenderer::new, renderDistance, commandList, minSection, maxSection, requestedThreads, true  /* hasShadowPass = true for Iris */);
        this.world = world;
        this.sectionCache = new ClonedChunkSectionCache(world);
        this.taskProvider = taskProvider;
    }

    public static AngelicaRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList) {
        final ChunkTaskProvider provider = ChunkTaskRegistry.getActiveProvider();
        final int minSection;
        final int maxSection;

        if (ModStatus.isCubicChunksLoaded && world instanceof ICubicWorld) {
            minSection = CubicChunksAPI.getMinSectionY(world);
            maxSection = CubicChunksAPI.getMaxSectionYExclusive(world);
        } else {
            minSection = 0;
            maxSection = 16;
        }

        return new AngelicaRenderSectionManager(AngelicaRenderPassConfiguration.build(vertexType), world, renderDistance, commandList, minSection, maxSection, provider.threadCount(), provider);
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
        if (isInShadowPass() && !needsUpdate()) {
            return;
        }
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
        return ClientProxy.options().performance.asyncOcclusionMode;
    }

    @Override
    protected boolean shouldRespectUpdateTaskQueueSizeLimit() {
        return true;
    }

    @Override
    protected boolean useFogOcclusion() {
        return ClientProxy.options().performance.useFogOcclusion && !IrisShaderProviderHolder.isActive();
    }

    @Override
    protected boolean isDebugInfoShown() {
        return Minecraft.getMinecraft().gameSettings.showDebugInfo;
    }

    @Override
    protected boolean shouldUseOcclusionCulling(Viewport positionedViewport, boolean spectator) {
        if (spectator) {
            final var camBlockPos = positionedViewport.getBlockCoord();
            if (this.world.getBlock(camBlockPos.x(), camBlockPos.y(), camBlockPos.z()).isOpaqueCube()) {
                return false;
            }
        }
        return ClientProxy.options().performance.useOcclusionCulling;
    }

    @Override
    public boolean isSectionVisuallyEmpty(int x, int y, int z) {
        final Chunk chunk = this.world.getChunkFromChunkCoords(x, z);
        if (chunk.isEmpty()) {
            return true;
        }

        if (ModStatus.isCubicChunksLoaded) {
            final var section = CubicChunksAPI.getCubeStorage(this.world, x, y, z);
            return section == null || section.isEmpty();
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

    public void onBiomesChanged(int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                this.biomeRebuildColumns.add(PositionUtil.packChunk(chunkX + dx, chunkZ + dz));
            }
        }
    }

    private void flushBiomeRebuilds() {
        if (this.biomeRebuildColumns.isEmpty()) {
            return;
        }
        if (Tracy.ENABLED) Tracy.beginZone(Z_BIOME_REBUILDS);
        try {
            final int sectionCountY = this.world.getHeight() >> 4;
            for (final LongIterator it = this.biomeRebuildColumns.iterator(); it.hasNext(); ) {
                final long column = it.nextLong();
                final int cx = PositionUtil.unpackChunkX(column);
                final int cz = PositionUtil.unpackChunkZ(column);
                for (int cy = 0; cy < sectionCountY; cy++) {
                    this.sectionCache.invalidate(cx, cy, cz);
                    this.scheduleRebuild(cx, cy, cz, false);
                }
            }
            this.biomeRebuildColumns.clear();
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    @Override
    public void updateChunks(boolean updateImmediately) {
        if (isInShadowPass()) {
            return;
        }
        this.sectionCache.cleanup();
        this.flushBiomeRebuilds();
        if (Tracy.ENABLED) Tracy.beginZone(Z_GRAPH_SEARCH);
        try {
            super.updateChunks(updateImmediately);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    @Override
    public void uploadChunks() {
        if (Tracy.ENABLED) Tracy.beginZone(Z_SECTION_UPLOAD);
        try {
            super.uploadChunks();
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    @Override
    protected boolean allowImportantRebuilds() {
        return !ClientProxy.options().performance.alwaysDeferChunkUpdates;
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

    public void markShadowGraphDirty() {
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.setNeedsUpdate(true);
        }
    }

    public boolean preSubmitShadowGraphUpdate(Viewport viewport, int frame, boolean spectator) {
        if (this.shadowRenderListManager == null || !this.shadowRenderListManager.isNeedsUpdate()) return false;
        if (((RenderListManagerAccessor) this.shadowRenderListManager).angelica$hasOcclusionFutureInFlight()) return false;

        final RenderSectionManagerAccessor self = (RenderSectionManagerAccessor) this;
        final int targetQueueSize = shouldRespectUpdateTaskQueueSizeLimit() ? (int) Math.min(Integer.MAX_VALUE, (long) getBuilder().getTargetQueueSize() * 10) : Integer.MAX_VALUE;
        this.shadowRenderListManager.startGraphUpdate(viewport, frame, self.angelica$getRegions().getRegionIdsLength(), self.angelica$getSearchDistance(), shouldUseOcclusionCulling(viewport, spectator), targetQueueSize);
        return true;
    }

    public boolean isShadowGraphDirty() {
        return this.shadowRenderListManager != null && this.shadowRenderListManager.isNeedsUpdate();
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
            if (Tracy.ENABLED) Tracy.beginZone(Z_SHADOW_GRAPH_WAIT);
            try {
                finishAllGraphUpdates();
            } finally {
                if (Tracy.ENABLED) Tracy.endZone();
            }
        }
        super.renderLayer(matrices, pass, occlusionCamera, camera);
    }

    /**
     * Process both asyncSubmittedTasks and AngelicaRenderQueue.TASKS.
     */
    @Override
    public void managedBlock(BooleanSupplier isDone) {
        final RenderSectionManagerAccessor accessor = (RenderSectionManagerAccessor) this;
        while (!isDone.getAsBoolean()) {
            Runnable task = accessor.angelica$getAsyncSubmittedTasks().poll();
            if (task != null) {
                task.run();
                continue;
            }

            if (AngelicaRenderQueue.processTasks(1) > 0) {
                continue;
            }

            LockSupport.parkNanos("Wait", 100000L);
        }
    }

    public void tracyPlots() {
        int regions = 0;
        for (var it = this.getRenderLists().iterator(); it.hasNext(); it.next()) {
            regions++;
        }
        Tracy.plotInt(P_MESH_REGIONS, regions);
        Tracy.plotInt(P_MESH_VISIBLE_CHUNKS, this.getVisibleChunkCount());
        if (this.shadowRenderListManager != null) {
            int shadowRegions = 0, shadowSections = 0;
            for (var it = this.shadowRenderListManager.getRenderLists().iterator(); it.hasNext(); ) {
                shadowSections += it.next().getSectionsWithGeometryCount();
                shadowRegions++;
            }
            Tracy.plotInt(P_SHADOW_REGIONS, shadowRegions);
            Tracy.plotInt(P_SHADOW_VISIBLE_CHUNKS, shadowSections);
        }
        Tracy.plotInt(P_MESH_SCHEDULED_JOBS, this.getBuilder().getScheduledJobCount());
        Tracy.plotInt(P_MESH_BUSY_THREADS, this.getBuilder().getBusyThreadCount());
        final var mem = this.getDeviceMemoryStats();
        Tracy.plotInt(P_MESH_DEVICE_USED, mem.deviceUsed + mem.indexUsed);
        Tracy.plotInt(P_MESH_DEVICE_ALLOCATED, mem.deviceAllocated + mem.indexAllocated);
    }
}
