package me.jellysquid.mods.sodium.client.render;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.managers.GLLightingManager;
import com.gtnewhorizons.angelica.mixins.interfaces.MinecraftAccessor;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import jss.notfine.core.SettingsManager;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.compat.FogHelper;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import com.gtnewhorizons.angelica.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.MultidrawChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.backends.oneshot.ChunkRenderBackendOneshot;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.DefaultModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTracker;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheShared;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.common.util.ListUtil;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.sodium.shadow_map.SwappableChunkRenderManager;
import net.coderbot.iris.sodium.vertex_format.IrisModelVertexFormats;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.block.Block;
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
import org.lwjgl.opengl.GL32;

import java.util.Set;

/**
 * Provides an extension to vanilla's {@link WorldRenderer}.
 */
public class SodiumWorldRenderer {
    private static SodiumWorldRenderer instance;

    private final Minecraft client;

    private WorldClient world;
    private int renderDistance;

    private double lastCameraX, lastCameraY, lastCameraZ;
    private float lastFov;
    private double lastCameraPitch, lastCameraYaw;
    private float lastFogDistance;

    private boolean useEntityCulling;

    private final Set<TileEntity> globalTileEntities = new ObjectOpenHashSet<>();


    @Getter private Frustrum frustum;
    private ChunkRenderManager<?> chunkRenderManager;
    private ChunkRenderBackend<?> chunkRenderBackend;

    // Iris
    private boolean wasRenderingShadows = false;

    private double iris$swapLastCameraX, iris$swapLastCameraY, iris$swapLastCameraZ, iris$swapLastCameraPitch, iris$swapLastCameraYaw;
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
            if (DynamicLights.isEnabled()){
                DynamicLights.get().clearLightSources();
            }
            this.unloadWorld();
        }

        // If we're loading a new world, load the renderer
        if (world != null) {
            this.loadWorld(world);
        }

    }

    private void loadWorld(WorldClient world) {
        this.world = world;

        ChunkRenderCacheShared.createRenderContext(this.world);

        this.initRenderer();
    }

    private void unloadWorld() {
        ChunkRenderCacheShared.destroyRenderContext(this.world);

        if (this.chunkRenderManager != null) {
            this.chunkRenderManager.destroy();
            this.chunkRenderManager = null;
        }

        if (this.chunkRenderBackend != null) {
            this.chunkRenderBackend.delete();
            this.chunkRenderBackend = null;
        }

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
            if(AngelicaConfig.enableIris) iris$ensureStateSwapped();
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

        this.processChunkEvents();

        this.useEntityCulling = SodiumClientMod.options().advanced.useEntityCulling;

        if (this.client.gameSettings.renderDistanceChunks != this.renderDistance) {
            this.reload();
        }

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

        float fogDistance = FogHelper.getFogCutoff();

        float fov = RenderingState.INSTANCE.getFov();

        boolean dirty = pos.x != this.lastCameraX || pos.y != this.lastCameraY || pos.z != this.lastCameraZ ||
                pitch != this.lastCameraPitch || yaw != this.lastCameraYaw || fogDistance != this.lastFogDistance || fov != this.lastFov;

        if(AngelicaConfig.enableIris) {
            iris$ensureStateSwapped();
            if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                dirty = true;
            }
        }
        if (dirty) {
            this.chunkRenderManager.markDirty();
        }

        this.lastCameraX = pos.x;
        this.lastCameraY = pos.y;
        this.lastCameraZ = pos.z;
        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;
        this.lastFogDistance = fogDistance;
        this.lastFov = fov;

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
        if(AngelicaConfig.enableIris) {
            if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                ShadowRenderer.visibleTileEntities.addAll(this.chunkRenderManager.getVisibleTileEntities());
            }
        }
    }

    private void processChunkEvents() {
        var tracker = ChunkTrackerHolder.get(this.world);
        tracker.forEachEvent(this.chunkRenderManager::onChunkAdded, this.chunkRenderManager::onChunkRemoved);
    }

    void drawLods(BlockRenderPass pass)
    {
        Mat4f mcModelViewMatrix = McObjectConverter.Convert(GLStateManager.getModelViewMatrix());
        Mat4f mcProjectionMatrix = McObjectConverter.Convert(GLStateManager.getProjectionMatrix());
        float frameTime = ((MinecraftAccessor)Minecraft.getMinecraft()).getTimer().renderPartialTicks;
        IClientLevelWrapper levelWrapper = ClientLevelWrapper.getWrapper(Minecraft.getMinecraft().theWorld);
        if (pass == BlockRenderPass.CUTOUT_MIPPED)
        {
            ClientApi.INSTANCE.renderLods(levelWrapper,
                mcModelViewMatrix,
                mcProjectionMatrix,
                frameTime);
            GLStateManager.glDepthFunc(GL32.GL_LEQUAL);
        }
    }

    /**
     * Performs a render pass for the given {@link RenderLayer} and draws all visible chunks for it.
     */
    public void drawChunkLayer(BlockRenderPass pass, MatrixStack matrixStack, double x, double y, double z) {
        drawLods(pass);
        // This fix a long-standing issue with culling state leaking because of mods,
        // or other factors as having clouds disabled.
        GLStateManager.enableCullFace();

        if(AngelicaConfig.enableIris) iris$ensureStateSwapped();
        // startDrawing/endDrawing are handled by 1.7 already
        // pass.startDrawing();

        this.chunkRenderManager.renderLayer(matrixStack, pass, x, y, z);

        //pass.endDrawing();
        GLLightingManager.clearCurrentColor();
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

        this.globalTileEntities.clear();

        RenderDevice device = RenderDevice.INSTANCE;

        this.renderDistance = this.client.gameSettings.renderDistanceChunks;

        SodiumGameOptions opts = SodiumClientMod.options();

        final ChunkVertexType vertexFormat;

        if(AngelicaConfig.enableIris && BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()) {
            vertexFormat = IrisModelVertexFormats.MODEL_VERTEX_XHFP;
        } else if (opts.advanced.useCompactVertexFormat) {
            vertexFormat = DefaultModelVertexFormats.MODEL_VERTEX_HFP;
        } else {
            vertexFormat = DefaultModelVertexFormats.MODEL_VERTEX_SFP;
        }

        this.chunkRenderBackend = createChunkRenderBackend(device, opts, vertexFormat);
        this.chunkRenderBackend.createShaders(device);

        this.chunkRenderManager = new ChunkRenderManager<>(this, this.chunkRenderBackend, this.world, this.renderDistance);

        var tracker = ChunkTrackerHolder.get(this.world);
        ChunkTracker.forEachChunk(tracker.getReadyChunks(), this.chunkRenderManager::onChunkAdded);
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
        var aabb = entity.getRenderBoundingBox();
        if (aabb == TileEntity.INFINITE_EXTENT_AABB) {
            return true;
        }
        return frustum.isBoundingBoxInFrustum(aabb);
    }

    private void renderTE(TileEntity tileEntity, int pass, float partialTicks) {
        if(!tileEntity.shouldRenderInPass(pass) || !checkBEVisibility(tileEntity))
            return;

        try {
            if(AngelicaConfig.enableIris) {
                final Block block = tileEntity.getWorldObj().getBlock(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                CapturedRenderingState.INSTANCE.setCurrentBlockEntity(Block.getIdFromBlock(block));
                GbufferPrograms.beginBlockEntities();
            }
            TileEntityRendererDispatcher.instance.renderTileEntity(tileEntity, partialTicks);

        } catch(RuntimeException e) {
            if(tileEntity.isInvalid()) {
                SodiumClientMod.logger().error("Suppressing crash from invalid tile entity", e);
            } else {
                throw e;
            }
        }
        finally {
            if(AngelicaConfig.enableIris) {
                CapturedRenderingState.INSTANCE.setCurrentBlockEntity(-1);
                GbufferPrograms.endBlockEntities();
            }
        }
    }

    public void renderTileEntities(EntityLivingBase entity, ICamera camera, float partialTicks) {
        final int pass = MinecraftForgeClient.getRenderPass();
        for (TileEntity tileEntity : this.chunkRenderManager.getVisibleTileEntities()) {
            renderTE(tileEntity, pass, partialTicks);
        }

        for (TileEntity tileEntity : this.globalTileEntities) {
            renderTE(tileEntity, pass, partialTicks);
        }
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

    // Iris
    private void swapCachedCameraPositions() {
        double tmp;

        tmp = lastCameraX;
        lastCameraX = iris$swapLastCameraX;
        iris$swapLastCameraX = tmp;

        tmp = lastCameraY;
        lastCameraY = iris$swapLastCameraY;
        iris$swapLastCameraY = tmp;

        tmp = lastCameraZ;
        lastCameraZ = iris$swapLastCameraZ;
        iris$swapLastCameraZ = tmp;

        tmp = lastCameraPitch;
        lastCameraPitch = iris$swapLastCameraPitch;
        iris$swapLastCameraPitch = tmp;

        tmp = lastCameraYaw;
        lastCameraYaw = iris$swapLastCameraYaw;
        iris$swapLastCameraYaw = tmp;
    }

    private void iris$ensureStateSwapped() {
        if (!wasRenderingShadows && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            if (this.chunkRenderManager instanceof SwappableChunkRenderManager) {
                ((SwappableChunkRenderManager) this.chunkRenderManager).iris$swapVisibilityState();
                swapCachedCameraPositions();
            }

            wasRenderingShadows = true;
        } else if (wasRenderingShadows && !ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            if (this.chunkRenderManager instanceof SwappableChunkRenderManager) {
                ((SwappableChunkRenderManager) this.chunkRenderManager).iris$swapVisibilityState();
                swapCachedCameraPositions();
            }

            wasRenderingShadows = false;
        }
    }
}
