package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightWorldRenderer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.MinecraftForgeClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.SortedRenderLists;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.List;

public class CeleritasWorldRenderer extends SimpleWorldRenderer<WorldClient, AngelicaRenderSectionManager, BlockRenderLayer, TileEntity, CeleritasWorldRenderer.TileEntityRenderContext> implements IDynamicLightWorldRenderer {
    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private final Minecraft mc;
    private static CeleritasWorldRenderer instance;

    private final TileEntityRenderContext teRenderContext = new TileEntityRenderContext();
    private boolean useEntityCulling = true;

    // the volume of a section multiplied by the number of sections to be checked at most
    private static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;

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
        super.unloadWorld();
    }

    @Override
    public int getEffectiveRenderDistance() {
        return mc.gameSettings.renderDistanceChunks;
    }

    @Override
    public int getMinimumBuildHeight() {
        return 0;
    }

    @Override
    public int getMaximumBuildHeight() {
        return 256;
    }

    @Override
    protected ChunkRenderMatrices createChunkRenderMatrices() {
        final boolean shadowPass = renderSectionManager.isInShadowPass();
        final Matrix4f projection = shadowPass ? ShadowRenderer.PROJECTION : RenderingState.INSTANCE.getProjectionMatrix();
        final Matrix4f modelView = shadowPass ? ShadowRenderer.MODELVIEW : RenderingState.INSTANCE.getModelViewMatrix();
        return new ChunkRenderMatrices(projection, modelView);
    }

    private ChunkVertexType chooseVertexType() {
        final IrisShaderProvider provider = IrisShaderProviderHolder.getProvider();
        if (provider != null && provider.isShadersEnabled()) {
            final ChunkVertexType extended = provider.getVertexType(ChunkMeshFormats.VANILLA_LIKE);
            if (extended != ChunkMeshFormats.VANILLA_LIKE) {
                return extended;
            }
        }

        if (SodiumClientMod.options().advanced.useCompactVertexFormat) {
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
        for (TileEntity tileEntity : list) {
            renderTE(tileEntity, context.pass, context.partialTicks);
        }
    }

    @Override
    public void setupTerrain(Viewport viewport, CameraState cameraState, int frame, boolean spectator, boolean updateChunksImmediately) {
        var transform = viewport.getTransform();

        if (transform.x == 0 && transform.y == 0 && transform.z == 0) {
            return;
        }

        renderSectionManager.setCameraPosition(transform.x, transform.y, transform.z);

        this.useEntityCulling = true;

        super.setupTerrain(viewport, cameraState, frame, spectator, updateChunksImmediately);

        // Collect tile entities for shadow pass rendering
        if (renderSectionManager.isInShadowPass() && IrisShaderProviderHolder.isActive()) {
            collectTileEntitiesForShadow();
        }
    }

    public void setCurrentViewport(Viewport viewport) {
        this.currentViewport = viewport;
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
                    ShadowRenderer.visibleTileEntities.addAll(culledEntities);
                }
            }
        }

        for (var renderSection : renderSectionManager.getSectionsWithGlobalEntities()) {
            final var context = renderSection.getBuiltContext();
            if (context instanceof MinecraftBuiltRenderSectionData<?, ?> mcData) {
                final var globalEntities = (List<TileEntity>) mcData.globalBlockEntities;
                ShadowRenderer.globalTileEntities.addAll(globalEntities);
            }
        }
    }

    @Override
    public void drawChunkLayer(BlockRenderLayer renderLayer, double x, double y, double z) {
        super.drawChunkLayer(renderLayer, x, y, z);
        GLStateManager.glColor4f(1, 1, 1, 1);
    }

    public int renderBlockEntities(float partialTicks) {
        final int pass = MinecraftForgeClient.getRenderPass();
        return super.renderBlockEntities(teRenderContext.set(partialTicks, pass));
    }

    private void renderTE(TileEntity tileEntity, int pass, float partialTicks) {
        if (!tileEntity.shouldRenderInPass(pass)) {
            return;
        }

        final AxisAlignedBB aabb = tileEntity.getRenderBoundingBox();

        if (aabb != TileEntity.INFINITE_EXTENT_AABB && !this.currentViewport.isBoxVisible(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ)) {
            return;
        }

        try {
            TileEntityRendererDispatcher.instance.renderTileEntity(tileEntity, partialTicks);
        } catch (RuntimeException e) {
            if (tileEntity.isInvalid()) {
                LOGGER.warn("Suppressed exception rendering invalid TileEntity {} at ({}, {}, {})", tileEntity.getClass().getName(), tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, e);
            } else {
                throw e;
            }
        }
    }

    private static boolean isInfiniteExtentsBox(AxisAlignedBB box) {
        return box == null || Double.isInfinite(box.minX) || Double.isInfinite(box.minY) || Double.isInfinite(box.minZ)
                || Double.isInfinite(box.maxX) || Double.isInfinite(box.maxY) || Double.isInfinite(box.maxZ);
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

        if (isInfiniteExtentsBox(box)) {
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
}
