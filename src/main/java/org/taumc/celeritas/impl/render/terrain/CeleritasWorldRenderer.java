package org.taumc.celeritas.impl.render.terrain;

import com.google.common.collect.Iterators;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.MinecraftForgeClient;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.SortedRenderLists;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.PositionUtil;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.taumc.celeritas.CeleritasArchaic;
import org.taumc.celeritas.impl.extensions.RenderGlobalExtension;
import org.taumc.celeritas.impl.render.terrain.compile.ArchaicRenderSectionBuiltInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Provides an extension to vanilla's {@link net.minecraft.client.renderer.RenderGlobal}.
 */
public class CeleritasWorldRenderer {
    private final Minecraft client;

    private WorldClient world;
    private int renderDistance;

    private double lastCameraX, lastCameraY, lastCameraZ;
    private double lastCameraPitch, lastCameraYaw;
    private float lastFogDistance;

    private boolean useEntityCulling;

    private Viewport currentViewport;

    @Getter
    private ArchaicRenderSectionManager renderSectionManager;

    /**
     * @return The CeleritasWorldRenderer based on the current dimension
     */
    public static CeleritasWorldRenderer instance() {
        var instance = instanceNullable();

        if (instance == null) {
            throw new IllegalStateException("No renderer attached to active world");
        }

        return instance;
    }

    /**
     * @return The CeleritasWorldRenderer based on the current dimension, or null if none is attached
     */
    public static CeleritasWorldRenderer instanceNullable() {
        var world = Minecraft.getMinecraft().renderGlobal;

        if (world instanceof RenderGlobalExtension extension) {
            return extension.sodium$getWorldRenderer();
        }

        return null;
    }

    public CeleritasWorldRenderer(Minecraft client) {
        this.client = client;
    }

    public void setWorld(WorldClient world) {
        // Check that the world is actually changing
        if (this.world == world) {
            return;
        }

        // If we have a world is already loaded, unload the renderer
        if (this.world != null) {
            this.unloadWorld();
        }

        // If we're loading a new world, load the renderer
        if (world != null) {
            this.loadWorld(world);
        }
    }

    private void loadWorld(WorldClient world) {
        this.world = world;

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    private void unloadWorld() {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.world = null;
    }

    /**
     * @return The number of chunk renders which are visible in the current camera's frustum
     */
    public int getVisibleChunkCount() {
        return this.renderSectionManager.getVisibleChunkCount();
    }

    /**
     * Notifies the chunk renderer that the graph scene has changed and should be re-computed.
     */
    public void scheduleTerrainUpdate() {
        // BUG: seems to be called before init
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    /**
     * @return True if no chunks are pending rebuilds
     */
    public boolean isTerrainRenderComplete() {
        return this.renderSectionManager.getBuilder().isBuildQueueEmpty();
    }

    public static int getEffectiveRenderDistance() {
        return Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
    }

    /**
     * Called prior to any chunk rendering in order to update necessary state.
     */
    public void setupTerrain(Viewport viewport,
                             float ticks,
                             @Deprecated(forRemoval = true) int frame,
                             boolean spectator,
                             boolean updateChunksImmediately) {
        NativeBuffer.reclaim(false);

        if (this.renderSectionManager != null) {
            this.renderSectionManager.finishAllGraphUpdates();
        }

        this.processChunkEvents();

        this.useEntityCulling = true;

        if (getEffectiveRenderDistance() != this.renderDistance) {
            this.reload();
        }

        Profiler profiler = Minecraft.getMinecraft().mcProfiler;
        profiler.startSection("camera_setup");

        Entity viewEntity = Objects.requireNonNull(this.client.renderViewEntity, "Client must have view entity");

        double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * ticks;
        double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * ticks + (double) viewEntity.getEyeHeight();
        double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * ticks;

        float pitch = viewEntity.rotationPitch;
        float yaw = viewEntity.rotationYaw;
        float fogDistance = ChunkShaderFogComponent.FOG_SERVICE.getFogCutoff();

        boolean dirty = x != this.lastCameraX || y != this.lastCameraY || z != this.lastCameraZ ||
                pitch != this.lastCameraPitch || yaw != this.lastCameraYaw || fogDistance != this.lastFogDistance;

        if (dirty) {
            this.renderSectionManager.markGraphDirty();
        }

        this.currentViewport = viewport;

        this.lastCameraX = x;
        this.lastCameraY = y;
        this.lastCameraZ = z;
        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;
        this.lastFogDistance = fogDistance;

        this.renderSectionManager.runAsyncTasks();

        profiler.endStartSection("chunk_update");

        this.renderSectionManager.updateChunks(updateChunksImmediately);

        profiler.endStartSection("chunk_upload");

        this.renderSectionManager.uploadChunks();

        if (this.renderSectionManager.needsUpdate()) {
            profiler.endStartSection("chunk_render_lists");

            this.renderSectionManager.update(viewport, frame, spectator);
        }

        if (updateChunksImmediately) {
            profiler.endStartSection("chunk_upload_immediately");

            this.renderSectionManager.uploadChunks();
        }

        profiler.endStartSection("chunk_render_tick");

        this.renderSectionManager.tickVisibleRenders();

        profiler.endSection();

        double entityDistanceScale = 1.0;

        //Entity.setRenderDistanceWeight(MathHelper.clamp((double) this.client.gameSettings.renderDistanceChunks / 8.0D, 1.0D, 2.5D) * 2000);

        //Entity.setViewScale(Mth.clamp((double) getEffectiveRenderDistance() / 8.0D, 1.0D, 2.5D) * entityDistanceScale);
    }

    private void processChunkEvents() {
        var tracker = ChunkTrackerHolder.get(this.world);
        tracker.forEachEvent(this.renderSectionManager::onChunkAdded, this.renderSectionManager::onChunkRemoved);
    }

    /**
     * Draws all visible chunks for the given pass.
     */
    public void drawChunkLayer(int vanillaPass, double x, double y, double z) {
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(
                new Matrix4f(ActiveRenderInfo.projection),
                new Matrix4f(ActiveRenderInfo.modelview)
        );

        Collection<TerrainRenderPass> passes = this.renderSectionManager.getRenderPassConfiguration().vanillaRenderStages().get(vanillaPass);

        if (passes != null && !passes.isEmpty()) {
            for (var pass : passes) {
                this.renderSectionManager.renderLayer(matrices, pass, x, y, z);
            }
        }

        GL11.glColor4f(1, 1, 1, 1);
    }

    public void reload() {
        if (this.world == null) {
            return;
        }

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    private void initRenderer(CommandList commandList) {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.renderDistance = getEffectiveRenderDistance();

        // TODO offer CVF
        ChunkVertexType vertexType = ChunkMeshFormats.VANILLA_LIKE;

        this.renderSectionManager = ArchaicRenderSectionManager.create(vertexType, this.world, this.renderDistance, commandList);

        var tracker = ChunkTrackerHolder.get(this.world);
        ChunkTracker.forEachChunk(tracker.getReadyChunks(), this.renderSectionManager::onChunkAdded);
    }

    // We track whether a block entity uses custom block outline rendering, so that the outline postprocessing
    // shader will be enabled appropriately
    private boolean blockEntityRequestedOutline;

    public boolean didBlockEntityRequestOutline() {
        return blockEntityRequestedOutline;
    }

    /**
     * {@return an iterator over all visible block entities}
     * <p>
     * Note that this method performs significantly more allocations and will generally be less efficient than
     * {@link CeleritasWorldRenderer#forEachVisibleBlockEntity(Consumer)}. It is intended only for situations where using
     * that method is not feasible.
     */
    public Iterator<TileEntity> blockEntityIterator() {
        List<Iterator<TileEntity>> iterators = new ArrayList<>();

        SortedRenderLists renderLists = this.renderSectionManager.getRenderLists();
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            var renderList = renderListIterator.next();

            var renderRegion = renderList.getRegion();
            var renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                var renderSectionId = renderSectionIterator.nextByteAsInt();
                var renderSection = renderRegion.getSection(renderSectionId);

                if (renderSection == null) {
                    continue;
                }

                var blockEntities = renderSection.getContextOrDefault(ArchaicRenderSectionBuiltInfo.CULLED_BLOCK_ENTITIES);

                if (blockEntities.isEmpty()) {
                    continue;
                }

                iterators.add(blockEntities.iterator());
            }
        }

        for (var renderSection : this.renderSectionManager.getSectionsWithGlobalEntities()) {
            var blockEntities = renderSection.getContextOrDefault(ArchaicRenderSectionBuiltInfo.GLOBAL_BLOCK_ENTITIES);

            if (blockEntities.isEmpty()) {
                continue;
            }

            iterators.add(blockEntities.iterator());
        }

        if(iterators.isEmpty()) {
            return Collections.emptyIterator();
        } else {
            return Iterators.concat(iterators.iterator());
        }
    }

    public void forEachVisibleBlockEntity(Consumer<TileEntity> consumer) {
        SortedRenderLists renderLists = this.renderSectionManager.getRenderLists();
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            var renderList = renderListIterator.next();

            var renderRegion = renderList.getRegion();
            var renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                var renderSectionId = renderSectionIterator.nextByteAsInt();
                var renderSection = renderRegion.getSection(renderSectionId);

                if (renderSection == null) {
                    continue;
                }

                var blockEntities = renderSection.getContextOrDefault(ArchaicRenderSectionBuiltInfo.CULLED_BLOCK_ENTITIES);
                blockEntities.forEach(consumer);
            }
        }

        for (var renderSection : this.renderSectionManager.getSectionsWithGlobalEntities()) {
            var blockEntities = renderSection.getContextOrDefault(ArchaicRenderSectionBuiltInfo.GLOBAL_BLOCK_ENTITIES);
            blockEntities.forEach(consumer);
        }
    }

    private void renderTE(TileEntity tileEntity, int pass, float partialTicks) {
        if(!tileEntity.shouldRenderInPass(pass))
            return;

        var aabb = tileEntity.getRenderBoundingBox();

        if (aabb != TileEntity.INFINITE_EXTENT_AABB && !this.currentViewport.isBoxVisible(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ)) {
            return;
        }

        try {
            TileEntityRendererDispatcher.instance.renderTileEntity(tileEntity, partialTicks);
        } catch(RuntimeException e) {
            if(tileEntity.isInvalid()) {
                CeleritasArchaic.logger().error("Suppressing crash from invalid tile entity", e);
            } else {
                throw e;
            }
        }
    }

    private void renderCulledBlockEntities(int pass, float partialTicks) {
        SortedRenderLists renderLists = this.renderSectionManager.getRenderLists();
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            var renderList = renderListIterator.next();

            var renderRegion = renderList.getRegion();
            var renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                var renderSectionId = renderSectionIterator.nextByteAsInt();
                var renderSection = renderRegion.getSection(renderSectionId);

                if (renderSection == null) {
                    continue;
                }

                var blockEntities = renderSection.getContextOrDefault(ArchaicRenderSectionBuiltInfo.CULLED_BLOCK_ENTITIES);

                if (blockEntities.isEmpty()) {
                    continue;
                }

                for (TileEntity blockEntity : blockEntities) {
                    renderTE(blockEntity, pass, partialTicks);
                }
            }
        }
    }

    private void renderGlobalBlockEntities(int pass, float partialTicks) {
        for (var renderSection : this.renderSectionManager.getSectionsWithGlobalEntities()) {
            var blockEntities = renderSection.getContextOrDefault(ArchaicRenderSectionBuiltInfo.GLOBAL_BLOCK_ENTITIES);

            if (blockEntities.isEmpty()) {
                continue;
            }

            for (var blockEntity : blockEntities) {
                renderTE(blockEntity, pass, partialTicks);
            }
        }
    }

    public void renderBlockEntities(float partialTicks, Map<Integer, DestroyBlockProgress> damagedBlocks) {
        int pass = MinecraftForgeClient.getRenderPass();

        this.renderCulledBlockEntities(pass, partialTicks);
        this.renderGlobalBlockEntities(pass, partialTicks);
    }


    // the volume of a section multiplied by the number of sections to be checked at most
    private static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;

    private static boolean isInfiniteExtentsBox(AxisAlignedBB box) {
        return Double.isInfinite(box.minX) || Double.isInfinite(box.minY) || Double.isInfinite(box.minZ)
                || Double.isInfinite(box.maxX) || Double.isInfinite(box.maxY) || Double.isInfinite(box.maxZ);
    }

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public boolean isEntityVisible(Entity entity) {
        if (!this.useEntityCulling) {
            return true;
        }

        AxisAlignedBB box = entity.getBoundingBox();


        if (isInfiniteExtentsBox(box)) {
            return true;
        }

        // bail on very large entities to avoid checking many sections
        double entityVolume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
        if (entityVolume > MAX_ENTITY_CHECK_VOLUME) {
            // TODO: do a frustum check instead, even large entities aren't visible if they're outside the frustum
            return true;
        }

        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Boxes outside the valid world height will never map to a rendered chunk
        // Always render these boxes or they'll be culled incorrectly!
        if (y2 < 0.5D || y1 > 255 - 0.5D) {
            return true;
        }

        int minX = PositionUtil.posToSectionCoord(x1 - 0.5D);
        int minY = PositionUtil.posToSectionCoord(y1 - 0.5D);
        int minZ = PositionUtil.posToSectionCoord(z1 - 0.5D);

        int maxX = PositionUtil.posToSectionCoord(x2 + 0.5D);
        int maxY = PositionUtil.posToSectionCoord(y2 + 0.5D);
        int maxZ = PositionUtil.posToSectionCoord(z2 + 0.5D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.renderSectionManager.isSectionVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getChunksDebugString() {
        // C: visible/total D: distance
        // TODO: add dirty and queued counts
        return String.format("C: %d/%d D: %d", this.renderSectionManager.getVisibleChunkCount(), this.renderSectionManager.getTotalSections(), this.renderDistance);
    }

    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderSectionManager.getRenderPassConfiguration();
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified block region.
     */
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified chunk region.
     */
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    /**
     * Schedules a chunk rebuild for the render belonging to the given chunk section position.
     */
    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        this.renderSectionManager.scheduleRebuild(x, y, z, important);
    }

    public Collection<String> getDebugStrings() {
        return this.renderSectionManager.getDebugStrings();
    }

    public boolean isSectionReady(int x, int y, int z) {
        return this.renderSectionManager.isSectionBuilt(x, y, z);
    }
}
