package com.gtnewhorizons.angelica.rendering.celeritas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.MinecraftForgeClient;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.SortedRenderLists;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.frustum.SimpleFrustum;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.cubicchunks.CubicChunksAPI;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightWorldRenderer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.mixins.interfaces.ITileEntityBoundingBoxCache;
import com.gtnewhorizons.angelica.profiling.RenderClassTimings;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import net.coderbot.iris.pipeline.ShadowRenderer;

public class CeleritasWorldRenderer extends SimpleWorldRenderer<WorldClient, AngelicaRenderSectionManager, BlockRenderLayer, TileEntity, CeleritasWorldRenderer.TileEntityRenderContext> implements IDynamicLightWorldRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private static final Tracy.ZoneId Z_TESR_COLLECT = Tracy.zoneId("tesrCollect", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_TESR_DISPATCH = Tracy.zoneId("tesrDispatch", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_TESR_MODEL_PARTS = Tracy.zoneId("tesrModelParts", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_TESR_RENDER_TE = Tracy.zoneId("tesrRenderTE", Tracy.COLOR_CLIENT);

    private final Minecraft mc;
    private static CeleritasWorldRenderer instance;

    /** Debug flag for wireframe rendering mode. Toggle via /angelica wireframe */
    public static boolean DEBUG_WIREFRAME_MODE = false;

    private final TileEntityRenderContext teRenderContext = new TileEntityRenderContext();
    private boolean useEntityCulling = true;

    private FrustumIntersection teFrustum;
    private CameraTransform teTransform;
    private int teCullEpoch;
    private double teCamX, teCamY, teCamZ;

    // the volume of a section multiplied by the number of sections to be checked at most
    private static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;

    private final ObjectArrayList<TileEntity> frameTEs = new ObjectArrayList<>();
    private final ObjectArrayList<TileEntity> frameOverride = new ObjectArrayList<>();

    // For sorting transparent TESRs
    private final TileEntityOrderer tileEntityOrderer = new TileEntityOrderer();
    private final ArrayList<TileEntity> sortedTileEntities = new ArrayList<>();

    private CeleritasWorldRenderer(Minecraft mc) {
        // Private constructor for singleton
        this.mc = mc;
    }

    public static CeleritasWorldRenderer create(Minecraft mc) {
        instance = new CeleritasWorldRenderer(mc);
        return instance;
    }

    public static CeleritasWorldRenderer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CeleritasWorldRenderer not initialized");
        }
        return instance;
    }

    public static CeleritasWorldRenderer getInstanceOrNull() {
        return instance;
    }

    public AngelicaRenderSectionManager getRenderSectionManager() {
        return this.renderSectionManager;
    }

    @Override
    public boolean isActive() {
        return this.world != null;
    }

    @Override
    protected void loadWorld(WorldClient world) {
        super.loadWorld(world);
        DynamicLights.setActiveRenderer(this);
    }

    @Override
    protected void unloadWorld() {
        DynamicLights.setActiveRenderer(null);
        this.frameTEs.clear();
        this.frameOverride.clear();
        this.sortedTileEntities.clear();
        ShadowRenderer.visibleTileEntities.clear();
        ShadowRenderer.globalTileEntities.clear();
        TesrBatchRenderer.INSTANCE.clearRetained();
        ModelPartBatcher.INSTANCE.clear();
        GpuCulling.onWorldUnload();
        super.unloadWorld();
    }

    @Override
    public int getEffectiveRenderDistance() {
        return mc.gameSettings.renderDistanceChunks;
    }

    @Override
    public int getMinimumBuildHeight() {
        if (ModStatus.isCubicChunksLoaded) {
            return CubicChunksAPI.getMinHeight(this.world);
        } else {
            return 0;
        }
    }

    @Override
    public int getMaximumBuildHeight() {
        if (ModStatus.isCubicChunksLoaded) {
            return CubicChunksAPI.getMaxHeight(this.world);
        } else {
            return 256;
        }
    }

    private final Matrix4f mainPassModelView = new Matrix4f();
    private ChunkRenderMatrices mainPassMatrices;
    private ChunkRenderMatrices shadowPassMatrices;

    @Override
    protected ChunkRenderMatrices createChunkRenderMatrices() {
        if (renderSectionManager.isInShadowPass()) {
            ChunkRenderMatrices matrices = shadowPassMatrices;
            if (matrices == null) {
                matrices = shadowPassMatrices = new ChunkRenderMatrices(ShadowRenderer.PROJECTION, ShadowRenderer.MODELVIEW);
            }
            return matrices;
        }
        // Minecraft's setupCameraTransform bakes translate(0, -eyeHeight, 0) into the modelview.
        // Chunk rendering passes eye position to drawChunkLayer so vertices arrive at
        // the shader as (vert_world - eye).
        mainPassModelView.set(RenderingState.INSTANCE.getModelViewMatrix());
        final Entity view = mc.renderViewEntity;
        if (view != null) {
            mainPassModelView.translate(0f, view.getEyeHeight(), 0f);
        }
        ChunkRenderMatrices matrices = mainPassMatrices;
        if (matrices == null) {
            matrices = mainPassMatrices = new ChunkRenderMatrices(RenderingState.INSTANCE.getProjectionMatrix(), mainPassModelView);
        }
        return matrices;
    }

    private ChunkVertexType chooseVertexType() {
        final IrisShaderProvider provider = IrisShaderProviderHolder.getProvider();
        if (provider != null && provider.isShadersEnabled()) {
            final ChunkVertexType extended = provider.getVertexType(ChunkMeshFormats.VANILLA_LIKE);
            if (extended != ChunkMeshFormats.VANILLA_LIKE) {
                return extended;
            }
        }

        if (ClientProxy.options().performance.useCompactVertexFormat) {
            return ChunkMeshFormats.COMPACT;
        }
        return ChunkMeshFormats.VANILLA_LIKE;
    }

    @Override
    protected AngelicaRenderSectionManager createRenderSectionManager(CommandList commandList) {
        return AngelicaRenderSectionManager.create(chooseVertexType(), this.world, this.renderDistance, commandList);
    }

    @Override
    protected void renderBlockEntityList(List<TileEntity> list, TileEntityRenderContext context) {
        for (int i = 0; i < list.size(); i++) {
            final TileEntity te = list.get(i);
            if (isTileEntityVisible(te)) {
                frameTEs.add(te);
            }
        }
    }

    private CameraState lastMainCameraState;

    @Override
    public void setupTerrain(Viewport viewport, CameraState cameraState, int frame, boolean spectator, boolean updateChunksImmediately) {
        var transform = viewport.getTransform();

        if (transform.x == 0 && transform.y == 0 && transform.z == 0) {
            return;
        }

        renderSectionManager.setCameraPosition(transform.x, transform.y, transform.z);

        this.useEntityCulling = ClientProxy.options().performance.useEntityCulling;

        if (renderSectionManager.isInShadowPass()) {
            if (lastMainCameraState != null) {
                cameraState = lastMainCameraState;
            }
        } else {
            lastMainCameraState = cameraState;
        }

        super.setupTerrain(viewport, cameraState, frame, spectator, updateChunksImmediately);

        // Process deferred dynamic light chunk rebuilds with frustum culling
        if (DynamicLights.isEnabled() && DynamicLights.FrustumCullingEnabled) {
            DynamicLights.get().processChunkRebuilds(viewport);
        }

        if (renderSectionManager.isInShadowPass()) {
            if (IrisShaderProviderHolder.isActive()) {
                collectTileEntitiesForShadow();
            }
        } else if (IrisShaderProviderHolder.isActive()) {
            IrisShaderProviderHolder.getProvider().preSubmitShadowGraph(frame, spectator);
        }
    }

    public void setCurrentViewport(Viewport viewport) {
        this.currentViewport = viewport;
    }

    public Viewport getCurrentViewport() {
        return this.currentViewport;
    }

    @SuppressWarnings("unchecked")
    private void collectTileEntitiesForShadow() {
        final SortedRenderLists renderLists = renderSectionManager.getRenderLists();
        final Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            final var renderList = renderListIterator.next();
            final var renderRegion = renderList.getRegion();
            final var renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                final var renderSectionId = renderSectionIterator.nextByteAsInt();
                final var renderSection = renderRegion.getSection(renderSectionId);

                if (renderSection == null) {
                    continue;
                }

                final var context = renderSection.getBuiltContext();
                if (context instanceof MinecraftBuiltRenderSectionData<?, ?> mcData) {
                    final var culledEntities = (List<TileEntity>) mcData.culledBlockEntities;
                    if (!culledEntities.isEmpty()) {
                        ShadowRenderer.visibleTileEntities.add(culledEntities);
                    }
                }
            }
        }

        for (var renderSection : renderSectionManager.getSectionsWithGlobalEntities()) {
            final var context = renderSection.getBuiltContext();
            if (context instanceof MinecraftBuiltRenderSectionData<?, ?> mcData) {
                final var globalEntities = (List<TileEntity>) mcData.globalBlockEntities;
                if (!globalEntities.isEmpty()) {
                    ShadowRenderer.globalTileEntities.add(globalEntities);
                }
            }
        }
    }

    private CameraTransform cachedCameraTransform;
    private double cachedCamX, cachedCamY, cachedCamZ;

    @Override
    public void drawChunkLayer(BlockRenderLayer renderLayer, double x, double y, double z) {
        if (DEBUG_WIREFRAME_MODE) {
            GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }

        final ChunkRenderMatrices matrices = createChunkRenderMatrices();
        final Collection<TerrainRenderPass> passes = this.renderSectionManager.getRenderPassConfiguration().vanillaRenderStages().get(renderLayer);
        if (passes != null && !passes.isEmpty()) {
            CameraTransform realCamera = cachedCameraTransform;
            if (realCamera == null || cachedCamX != x || cachedCamY != y || cachedCamZ != z) {
                realCamera = cachedCameraTransform = new CameraTransform(x, y, z);
                cachedCamX = x;
                cachedCamY = y;
                cachedCamZ = z;
            }
            final CameraTransform occlusionCamera = this.getLastViewport().getTransform();
            for (final TerrainRenderPass pass : passes) {
                this.renderSectionManager.renderLayer(matrices, pass, occlusionCamera, realCamera);
            }
        }

        if (DEBUG_WIREFRAME_MODE) {
            GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }

        GLStateManager.glColor4f(1, 1, 1, 1);
    }

    public int renderBlockEntities(float partialTicks) {
        AngelicaTesrMeshCache.INSTANCE.tick();
        final int pass = MinecraftForgeClient.getRenderPass();

        TesrBatchRenderer.INSTANCE.beginPass(
            pass == 0 ? TesrBatchRenderer.PASS_MAIN_0 : TesrBatchRenderer.PASS_MAIN_1,
            GLStateManager.getModelViewMatrix(),
            TileEntityRendererDispatcher.staticPlayerX,
            TileEntityRendererDispatcher.staticPlayerY,
            TileEntityRendererDispatcher.staticPlayerZ);
        ModelPartBatcher.INSTANCE.begin(ModelPartBatcher.Mode.BLOCK_ENTITIES);
        int count = 0;
        if (pass == 0) {
            teRenderContext.set(partialTicks, pass);
            final Viewport viewport = this.currentViewport;
            this.teTransform = viewport != null ? viewport.getTransform() : null;
            this.teFrustum = viewport != null && ClientProxy.options().performance.sectionGatedTesrCulling && viewport.getFrustum() instanceof SimpleFrustum simpleFrustum ? simpleFrustum.frustumIntersection() : null;
            this.teCullEpoch++;
            this.teCamX = TileEntityRendererDispatcher.staticPlayerX;
            this.teCamY = TileEntityRendererDispatcher.staticPlayerY;
            this.teCamZ = TileEntityRendererDispatcher.staticPlayerZ;
            frameTEs.clear();
            frameOverride.clear();
            if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_COLLECT);
            try {
                super.renderBlockEntities(teRenderContext);
            } finally {
                if (Tracy.ENABLED) Tracy.endZone();
            }
            this.teFrustum = null;
            this.teTransform = null;
            if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_DISPATCH);
            try {
                for (int i = 0; i < frameTEs.size(); i++) {
                    final TileEntity te = frameTEs.get(i);
                    if (((ITileEntityBoundingBoxCache) te).angelica$passClass() == TileEntityRenderBoundsRegistry.PASS0_ONLY) {
                        dispatchTE(te, partialTicks);
                        count++;
                        continue;
                    }
                    frameOverride.add(te);
                    if (te.shouldRenderInPass(0)) {
                        dispatchTE(te, partialTicks);
                        count++;
                    }
                }
            } finally {
                if (Tracy.ENABLED) Tracy.endZone();
            }
        } else {
            if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_DISPATCH);
            try {
                sortedTileEntities.clear();
                for (int i = 0; i < frameOverride.size(); i++) {
                    final TileEntity te = frameOverride.get(i);
                    if (te.shouldRenderInPass(pass)) {
                        sortedTileEntities.add(te);
                    }
                }
                if (sortedTileEntities.size() > 1 && ClientProxy.options().performance.translucencySorting) {
                    sortedTileEntities.sort(tileEntityOrderer.setLastCameraState(lastCameraState));
                }
                for (int i = 0; i < sortedTileEntities.size(); i++) {
                    dispatchTE(sortedTileEntities.get(i), partialTicks);
                }
                count = sortedTileEntities.size();
            } finally {
                if (Tracy.ENABLED) Tracy.endZone();
            }
        }
        if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_MODEL_PARTS);
        try {
            ModelPartBatcher.INSTANCE.flush();
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
        TesrBatchRenderer.INSTANCE.flush();
        return count;
    }

    @Override
    protected int renderSectionBlockEntities(RenderSection section, List<TileEntity> blockEntities, boolean culled, TileEntityRenderContext renderContext) {
        if (culled) {
            if (section.getBuiltContext() instanceof AngelicaBuiltRenderSectionData angelicaData) {
                if (TeDistanceMath.distSqToSection(teCamX, teCamY, teCamZ,
                    section.getOriginX(), section.getOriginY(), section.getOriginZ()) >= angelicaData.maxTeRenderDistSq) {
                    return 0;
                }
                if (teFrustum != null) {
                    return collectGatedSectionBlockEntities(section, angelicaData, blockEntities);
                }
            }

            int count = 0;
            for (int i = 0; i < blockEntities.size(); i++) {
                final TileEntity te = blockEntities.get(i);
                if (isTileEntityVisible(te)) {
                    frameTEs.add(te);
                    count++;
                }
            }
            return count;
        }

        int count = 0;
        for (int i = 0; i < blockEntities.size(); i++) {
            final TileEntity te = blockEntities.get(i);
            if (!(te.getDistanceFrom(teCamX, teCamY, teCamZ) < te.getMaxRenderDistanceSquared())) continue;
            if (isTileEntityVisible(te)) {
                frameTEs.add(te);
                count++;
            }
        }
        return count;
    }

    private int collectGatedSectionBlockEntities(RenderSection section, AngelicaBuiltRenderSectionData angelicaData, List<TileEntity> blockEntities) {
        final CameraTransform t = this.teTransform;
        final float ox = (section.getOriginX() - t.intX) - t.fracX;
        final float oy = (section.getOriginY() - t.intY) - t.fracY;
        final float oz = (section.getOriginZ() - t.intZ) - t.fracZ;

        final int verdict = sectionVerdict(angelicaData, ox, oy, oz);
        if (verdict >= 0) {
            return 0;
        }

        if (verdict == FrustumIntersection.INSIDE) {
            frameTEs.addAll(blockEntities);
            return blockEntities.size();
        }

        final float[] bounds = angelicaData.culledBlockEntityBounds;
        if (bounds.length != blockEntities.size() * 6) {
            throw new IllegalStateException("culledBlockEntityBounds desync: " + bounds.length + " floats for " + blockEntities.size() + " tile entities");
        }

        int count = 0;
        for (int i = 0, b = 0; i < blockEntities.size(); i++, b += 6) {
            if (teFrustum.testAab(ox + bounds[b], oy + bounds[b + 1], oz + bounds[b + 2],
                ox + bounds[b + 3], oy + bounds[b + 4], oz + bounds[b + 5])) {
                frameTEs.add(blockEntities.get(i));
                count++;
            }
        }
        return count;
    }

    private int sectionVerdict(AngelicaBuiltRenderSectionData angelicaData, float ox, float oy, float oz) {
        if (angelicaData.cullEpoch == this.teCullEpoch) {
            return angelicaData.cullVerdict;
        }
        final int verdict = this.teFrustum.intersectAab(ox, oy, oz, ox + 16.0f, oy + 16.0f, oz + 16.0f);
        angelicaData.cullEpoch = this.teCullEpoch;
        angelicaData.cullVerdict = verdict;
        return verdict;
    }

    private boolean isTileEntityVisible(TileEntity tileEntity) {
        final ITileEntityBoundingBoxCache teCache = (ITileEntityBoundingBoxCache) tileEntity;

        final byte boundsClass = teCache.angelica$boundsClass();
        if (boundsClass != TileEntityRenderBoundsRegistry.INFINITE) {
            final AxisAlignedBB aabb = boundsClass == TileEntityRenderBoundsRegistry.DYNAMIC ? tileEntity.getRenderBoundingBox() : teCache.angelica$getCachedRenderBoundingBox();
            return aabb == null || isAabbVisible(aabb);
        }
        return true;
    }

    private boolean isAabbVisible(AxisAlignedBB aabb) {
        final FrustumIntersection frustum = this.teFrustum;
        if (frustum == null) {
            return this.currentViewport.isBoxVisible(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
        }
        final CameraTransform t = this.teTransform;
        return frustum.testAab(
            (float) (aabb.minX - t.intX) - t.fracX,
            (float) (aabb.minY - t.intY) - t.fracY,
            (float) (aabb.minZ - t.intZ) - t.fracZ,
            (float) (aabb.maxX - t.intX) - t.fracX,
            (float) (aabb.maxY - t.intY) - t.fracY,
            (float) (aabb.maxZ - t.intZ) - t.fracZ);
    }

    private final Reference2ObjectOpenHashMap<Class<?>, Class<?>> teLabelClasses = new Reference2ObjectOpenHashMap<>();

    private Class<?> teLabelClass(TileEntity tileEntity) {
        final Class<?> teClass = tileEntity.getClass();
        Class<?> label = teLabelClasses.get(teClass);
        if (label == null) {
            final TileEntitySpecialRenderer renderer = TileEntityRendererDispatcher.instance.getSpecialRenderer(tileEntity);
            label = renderer != null ? renderer.getClass() : teClass;
            teLabelClasses.put(teClass, label);
        }
        return label;
    }

    private void dispatchTE(TileEntity tileEntity, float partialTicks) {
        try {
            final long start = Tracy.ENABLED ? System.nanoTime() : 0L;
            final boolean attribute = Tracy.ENABLED && TesrAttribution.currentRenderable == null;
            if (attribute) TesrAttribution.currentRenderable = tileEntity.getClass();
            if (Tracy.ENABLED) Tracy.beginZone(Z_TESR_RENDER_TE);
            try {
                TileEntityRendererDispatcher.instance.renderTileEntity(tileEntity, partialTicks);
            } finally {
                if (Tracy.ENABLED) Tracy.endZone();
                if (attribute) TesrAttribution.currentRenderable = null;
                if (Tracy.ENABLED) RenderClassTimings.TESR.add(teLabelClass(tileEntity), System.nanoTime() - start);
            }
        } catch (RuntimeException e) {
            if (tileEntity.isInvalid()) {
                LOGGER.warn("Suppressed exception rendering invalid TileEntity {} at ({}, {}, {})", tileEntity.getClass().getName(), tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, e);
            } else {
                throw e;
            }
        }
    }

    public boolean isEntityVisible(Entity entity) {
        // During shadow pass, don't cull entities - shadow rendering uses different frustum
        if (!this.useEntityCulling || this.renderSectionManager.isInShadowPass()) {
            return true;
        }

        AxisAlignedBB box = entity.getBoundingBox();
        if (box == null) {
            box = entity.boundingBox;
        }

        if (TileEntityRenderBoundsRegistry.isInfiniteExtentsBox(box)) {
            return true;
        }

        // bail on very large entities to avoid checking many sections
        final double entityVolume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
        if (entityVolume > MAX_ENTITY_CHECK_VOLUME) {
            // TODO: do a frustum check instead, even large entities aren't visible if they're outside the frustum
            return true;
        }

        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static class TileEntityRenderContext {
        public float partialTicks;
        public int pass;

        public TileEntityRenderContext set(float partialTicks, int pass) {
            this.partialTicks = partialTicks;
            this.pass = pass;
            return this;
        }
    }

    private static class TileEntityOrderer implements Comparator<TileEntity> {
        public CameraState lastCameraState;

        public TileEntityOrderer() {
            super();
        }

        public TileEntityOrderer setLastCameraState(CameraState lastCameraState) {
            this.lastCameraState = lastCameraState;
            return this;
        }

        @Override
        public int compare(TileEntity te1, TileEntity te2) {
            final double x = this.lastCameraState.x();
            final double y = this.lastCameraState.y();
            final double z = this.lastCameraState.z();
            final double d1 = te1.getDistanceFrom(x, y, z);
            final double d2 = te2.getDistanceFrom(x, y, z);
            return Double.compare(d2, d1);
        }
    }
}
