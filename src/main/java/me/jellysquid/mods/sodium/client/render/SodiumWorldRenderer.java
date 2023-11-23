package me.jellysquid.mods.sodium.client.render;

import com.gtnewhorizons.angelica.compat.mojang.BlockBreakingInfo;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BufferBuilderStorage;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.ChunkPos;
import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import com.gtnewhorizons.angelica.compat.mojang.ModelLoader;
import com.gtnewhorizons.angelica.compat.mojang.OverlayVertexConsumer;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.compat.mojang.VertexConsumer;
import com.gtnewhorizons.angelica.compat.mojang.VertexConsumerProvider;
import com.gtnewhorizons.angelica.compat.mojang.VertexConsumers;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.early.sodium.MixinEntity;
import com.gtnewhorizons.angelica.mixins.interfaces.IHasClientChunkProvider;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import jss.notfine.core.SettingsManager;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.MultidrawChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.backends.oneshot.ChunkRenderBackendOneshot;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.DefaultModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheShared;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.IChunkProviderClientExt;
import me.jellysquid.mods.sodium.common.util.ListUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.MinecraftForgeClient;
import org.joml.Vector3d;
import speiger.src.collections.longs.maps.interfaces.Long2ObjectMap;

import java.util.Set;
import java.util.SortedSet;

/**
 * Provides an extension to vanilla's {@link WorldRenderer}.
 */
public class SodiumWorldRenderer implements ChunkStatusListener {
    private static SodiumWorldRenderer instance;

    private final Minecraft client;

    private WorldClient world;
    private int renderDistance;

    private double lastCameraX, lastCameraY, lastCameraZ;
    private double lastCameraPitch, lastCameraYaw;

    private boolean useEntityCulling;

    private final LongSet loadedChunkPositions = new LongOpenHashSet();
    private final Set<TileEntity> globalTileEntities = new ObjectOpenHashSet<>();

    /**
     * -- GETTER --
     *
     * @return The frustum of the current player's camera used to cull chunks
     */
    @Getter
    private Frustrum frustum;
    private ChunkRenderManager<?> chunkRenderManager;
    private BlockRenderPassManager renderPassManager;
    private ChunkRenderBackend<?> chunkRenderBackend;

    /**
     * Instantiates Sodium's world renderer. This should be called at the time of the world renderer initialization.
     */
    public static SodiumWorldRenderer create(Minecraft mc) {
        if (instance == null) {
            instance = new SodiumWorldRenderer(mc);
        }

        return instance;
    }

    /**
     * @throws IllegalStateException If the renderer has not yet been created
     * @return The current instance of this type
     */
    public static SodiumWorldRenderer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Renderer not initialized");
        }

        return instance;
    }

    private SodiumWorldRenderer(Minecraft client) {
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

    public int getChunksSubmitted() {
        return this.chunkRenderManager != null ? this.chunkRenderManager.getAndResetSubmitted() : 0;
    }

    private void loadWorld(WorldClient world) {
        this.world = world;

        ChunkRenderCacheShared.createRenderContext(this.world);

        this.initRenderer();

        ((IChunkProviderClientExt) ((IHasClientChunkProvider)world).getClientChunkProvider()).setListener(this);
    }

    private void unloadWorld() {
        ChunkRenderCacheShared.destroyRenderContext((BlockRenderView)this.world);

        if (this.chunkRenderManager != null) {
            this.chunkRenderManager.destroy();
            this.chunkRenderManager = null;
        }

        if (this.chunkRenderBackend != null) {
            this.chunkRenderBackend.delete();
            this.chunkRenderBackend = null;
        }

        this.loadedChunkPositions.clear();
        this.globalTileEntities.clear();

        this.world = null;
    }

    /**
     * @return The number of chunk renders which are visible in the current camera's frustum
     */
    public int getVisibleChunkCount() {
        return this.chunkRenderManager.getVisibleChunkCount();
    }

    /**
     * Notifies the chunk renderer that the graph scene has changed and should be re-computed.
     */
    public void scheduleTerrainUpdate() {
        if (this.chunkRenderManager != null) {
            this.chunkRenderManager.markDirty();
        }
    }

    /**
     * @return True if no chunks are pending rebuilds
     */
    public boolean isTerrainRenderComplete() {
        return this.chunkRenderManager.isBuildComplete();
    }

    // We'll keep it to have compatibility with Oculus' older versions
    public static boolean hasChanges = false;

    /**
     * Called prior to any chunk rendering in order to update necessary state.
     */
    public void updateChunks(Camera camera, Frustrum frustum, boolean hasForcedFrustum, int frame, boolean spectator) {
        this.frustum = frustum;

        this.useEntityCulling = SodiumClientMod.options().advanced.useEntityCulling;

        Profiler profiler = this.client.mcProfiler;
        profiler.startSection("camera_setup");

        EntityPlayer player = this.client.thePlayer;

        if (player == null) {
            throw new IllegalStateException("Client instance has no active player entity");
        }

        Vector3d pos = camera.getPos();

        this.chunkRenderManager.setCameraPosition(pos.x, pos.y, pos.z);

        float pitch = camera.getPitch();
        float yaw = camera.getYaw();

        boolean dirty = pos.x != this.lastCameraX || pos.y != this.lastCameraY || pos.z != this.lastCameraZ ||
                pitch != this.lastCameraPitch || yaw != this.lastCameraYaw;

        if (dirty) {
            this.chunkRenderManager.markDirty();
        }

        this.lastCameraX = pos.x;
        this.lastCameraY = pos.y;
        this.lastCameraZ = pos.z;
        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;

        profiler.endStartSection("chunk_update");

        this.chunkRenderManager.updateChunks();

        if (!hasForcedFrustum && this.chunkRenderManager.isDirty()) {
            profiler.endStartSection("chunk_graph_rebuild");

            this.chunkRenderManager.update(camera, (FrustumExtended) frustum, frame, spectator);
        }

        profiler.endStartSection("visible_chunk_tick");

        this.chunkRenderManager.tickVisibleRenders();

        profiler.endSection();

        SodiumGameOptions.EntityRenderDistance.setRenderDistanceMult(MathHelper.clamp_double((double) this.client.gameSettings.renderDistanceChunks / 8.0D, 1.0D, 2.5D) * (double) 1.0F * (SettingsManager.entityRenderScaleFactor));
    }

    /**
     * Performs a render pass for the given {@link RenderLayer} and draws all visible chunks for it.
     */
    public void drawChunkLayer(RenderLayer renderLayer, MatrixStack matrixStack, double x, double y, double z) {
        BlockRenderPass pass = this.renderPassManager.getRenderPassForLayer(renderLayer);
        // startDrawing/endDrawing are handled by 1.7 already
        //pass.startDrawing();

        this.chunkRenderManager.renderLayer(matrixStack, pass, x, y, z);

        //pass.endDrawing();

        GLStateManager.clearCurrentColor();
    }

    public void reload() {
        if (this.world == null) {
            return;
        }

        this.initRenderer();
    }

    private void initRenderer() {
        if (this.chunkRenderManager != null) {
            this.chunkRenderManager.destroy();
            this.chunkRenderManager = null;
        }

        if (this.chunkRenderBackend != null) {
            this.chunkRenderBackend.delete();
            this.chunkRenderBackend = null;
        }

        RenderDevice device = RenderDevice.INSTANCE;

        SodiumGameOptions opts = SodiumClientMod.options();

        this.renderPassManager = BlockRenderPassManager.createDefaultMappings();

        final ChunkVertexType vertexFormat;

        if (opts.advanced.useCompactVertexFormat) {
            vertexFormat = DefaultModelVertexFormats.MODEL_VERTEX_HFP;
        } else {
            vertexFormat = DefaultModelVertexFormats.MODEL_VERTEX_SFP;
        }

        this.chunkRenderBackend = createChunkRenderBackend(device, opts, vertexFormat);
        this.chunkRenderBackend.createShaders(device);

        this.chunkRenderManager = new ChunkRenderManager<>(this, this.chunkRenderBackend, this.renderPassManager, this.world, this.client.gameSettings.renderDistanceChunks);
        this.chunkRenderManager.restoreChunks(this.loadedChunkPositions);
    }

    private static ChunkRenderBackend<?> createChunkRenderBackend(RenderDevice device, SodiumGameOptions options, ChunkVertexType vertexFormat) {
        boolean disableBlacklist = SodiumClientMod.options().advanced.ignoreDriverBlacklist;

        if (options.advanced.useChunkMultidraw && MultidrawChunkRenderBackend.isSupported(disableBlacklist)) {
            return new MultidrawChunkRenderBackend(device, vertexFormat);
        } else {
            return new ChunkRenderBackendOneshot(vertexFormat);
        }
    }

    private boolean checkBEVisibility(TileEntity entity) {
        return frustum.isBoundingBoxInFrustum(entity.getRenderBoundingBox());
    }

    public void renderTileEntities(EntityLivingBase entity, ICamera camera, float partialTicks) {
//        VertexConsumerProvider.Immediate immediate = bufferBuilders.getEntityVertexConsumers();
//
//        Vector3d cameraPos = camera.getPos();
//        double x = cameraPos.x;
//        double y = cameraPos.y;
//        double z = cameraPos.z;
//
        int pass = MinecraftForgeClient.getRenderPass();
        for (TileEntity tileEntity : this.chunkRenderManager.getVisibleBlockEntities()) {
            if(tileEntity.isInvalid() || !tileEntity.shouldRenderInPass(pass) || !checkBEVisibility(tileEntity))
                continue;
//            BlockPos pos = new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
//
//            matrices.push();
//            matrices.translate((double) pos.x - x, (double) pos.y - y, (double) pos.z - z);
//
//            VertexConsumerProvider consumer = immediate;
//            SortedSet<BlockBreakingInfo> breakingInfos = blockBreakingProgressions.get(pos.asLong());
//
//            if (breakingInfos != null && !breakingInfos.isEmpty()) {
//                int stage = breakingInfos.last().getStage();
//
//                if (stage >= 0) {
//                    MatrixStack.Entry entry = matrices.peek();
//                    VertexConsumer transformer = new OverlayVertexConsumer(bufferBuilders.getEffectVertexConsumers().getBuffer(ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(stage)), entry.getModel(), entry.getNormal());
//                    consumer = (layer) -> layer.hasCrumbling() ? VertexConsumers.union(transformer, immediate.getBuffer(layer)) : immediate.getBuffer(layer);
//                }
//            }
            // TODO: Sodium TileEntity Rendering Dispatcher
            TileEntityRendererDispatcher.instance.renderTileEntity(tileEntity, partialTicks);
//            BlockEntityRenderDispatcher.INSTANCE.render(tileEntity, tickDelta, matrices, consumer);

//            matrices.pop();
        }

        for (TileEntity tileEntity : this.globalTileEntities) {
            if(!tileEntity.shouldRenderInPass(pass) || !checkBEVisibility(tileEntity))
                continue;
//            BlockPos pos = new BlockPos(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
//
//            matrices.push();
//            matrices.translate((double) pos.x - x, (double) pos.y - y, (double) pos.z - z);

            // TODO: Sodium TileEntity Rendering Dispatcher
            TileEntityRendererDispatcher.instance.renderTileEntity(tileEntity, partialTicks);
//            BlockEntityRenderDispatcher.INSTANCE.render(tileEntity, tickDelta, matrices, immediate);

//            matrices.pop();
        }
    }

    @Override
    public void onChunkAdded(int x, int z) {
        this.loadedChunkPositions.add(ChunkPos.toLong(x, z));
        this.chunkRenderManager.onChunkAdded(x, z);
    }

    @Override
    public void onChunkRemoved(int x, int z) {
        this.loadedChunkPositions.remove(ChunkPos.toLong(x, z));
        this.chunkRenderManager.onChunkRemoved(x, z);
    }

    public void onChunkRenderUpdated(int x, int y, int z, ChunkRenderData meshBefore, ChunkRenderData meshAfter) {
        ListUtil.updateList(this.globalTileEntities, meshBefore.getGlobalTileEntities(), meshAfter.getGlobalTileEntities());

        this.chunkRenderManager.onChunkRenderUpdates(x, y, z, meshAfter);
    }

    private static boolean isInfiniteExtentsBox(AxisAlignedBB box) {
        return Double.isInfinite(box.minX) || Double.isInfinite(box.minY) || Double.isInfinite(box.minZ)
            || Double.isInfinite(box.maxX) || Double.isInfinite(box.maxY) || Double.isInfinite(box.maxZ);
    }

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public boolean isEntityVisible(Entity entity) {
        if (!this.useEntityCulling || entity.ignoreFrustumCheck) {
            return true;
        }

        AxisAlignedBB box = entity.boundingBox;

        // Entities outside the valid world height will never map to a rendered chunk
        // Always render these entities or they'll be culled incorrectly!
        if (box.maxY < 0.5D || box.minY > 255.5D) {
            return true;
        }

        if (isInfiniteExtentsBox(box)) {
            return true;
        }

        // Ensure entities with outlines or nametags are always visible
        // TODO: Sodium - Outlines
        if (/*this.client.hasOutline(entity) || */ (entity instanceof EntityLiving living && living.hasCustomNameTag())) {
            return true;
        }

        int minX = MathHelper.floor_double(box.minX - 0.5D) >> 4;
        int minY = MathHelper.floor_double(box.minY - 0.5D) >> 4;
        int minZ = MathHelper.floor_double(box.minZ - 0.5D) >> 4;

        int maxX = MathHelper.floor_double(box.maxX + 0.5D) >> 4;
        int maxY = MathHelper.floor_double(box.maxY + 0.5D) >> 4;
        int maxZ = MathHelper.floor_double(box.maxZ + 0.5D) >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.chunkRenderManager.isChunkVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getChunksDebugString() {
        // C: visible/total
        // TODO: add dirty and queued counts
        return String.format("C: %s/%s S: %s Q: %s+%si ", this.chunkRenderManager.getVisibleChunkCount(), this.chunkRenderManager.getTotalSections(), this.chunkRenderManager.getSubmitted(), this.chunkRenderManager.getRebuildQueueSize(), this.chunkRenderManager.getImportantRebuildQueueSize());
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
        this.chunkRenderManager.scheduleRebuild(x, y, z, important);
    }

    public ChunkRenderBackend<?> getChunkRenderer() {
        return this.chunkRenderBackend;
    }
}
