package org.taumc.celeritas.impl.render.terrain;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.embeddium.impl.common.datastructure.ContextBundle;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.render.chunk.lists.SectionTicker;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.sprite.GenericSectionSpriteTicker;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.jetbrains.annotations.Nullable;
import org.taumc.celeritas.impl.render.terrain.compile.ArchaicChunkBuildContext;
import org.taumc.celeritas.impl.render.terrain.compile.ArchaicRenderSectionBuiltInfo;
import org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask;
import org.taumc.celeritas.impl.render.terrain.sprite.SpriteUtil;
import org.taumc.celeritas.impl.world.cloned.ChunkRenderContext;

import java.util.Collection;
import java.util.List;

public class ArchaicRenderSectionManager extends RenderSectionManager {
    private final WorldClient world;
    private final ReferenceSet<RenderSection> sectionsWithGlobalEntities = new ReferenceOpenHashSet<>();

    public ArchaicRenderSectionManager(RenderPassConfiguration<?> configuration, WorldClient world, int renderDistance, CommandList commandList, int minSection, int maxSection, int requestedThreads) {
        super(configuration, () -> new ArchaicChunkBuildContext(world, configuration), ArchaicChunkRenderer::new, renderDistance, commandList, minSection, maxSection, requestedThreads);
        this.world = world;
    }

    public static ArchaicRenderSectionManager create(ChunkVertexType vertexType, WorldClient world, int renderDistance, CommandList commandList) {
        // TODO support thread option
        return new ArchaicRenderSectionManager(ArchaicRenderPassConfigurationBuilder.build(vertexType), world, renderDistance, commandList, 0, 16, -1);
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
    protected boolean shouldUseOcclusionCulling(Viewport positionedViewport, boolean spectator) {
        final boolean useOcclusionCulling;
        var camBlockPos = positionedViewport.getBlockCoord();

        useOcclusionCulling = !spectator || !this.world.getBlock(camBlockPos.x(), camBlockPos.y(), camBlockPos.z()).isOpaqueCube();

        return useOcclusionCulling;
    }

    @Override
    protected boolean isSectionVisuallyEmpty(int x, int y, int z) {
        Chunk chunk = this.world.getChunkFromChunkCoords(x, z);
        if (chunk.isEmpty()) {
            return true;
        }
        var array = chunk.getBlockStorageArray();
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

        ChunkRenderContext context = new ChunkRenderContext(new SectionPos(render.getChunkX(), render.getChunkY(), render.getChunkZ()));

        return new ChunkBuilderMeshingTask(render, context, frame, this.cameraPosition);
    }

    @Override
    protected boolean allowImportantRebuilds() {
        return false;
    }

    @Override
    protected void updateSectionInfo(RenderSection render, ContextBundle<RenderSection> info) {
        super.updateSectionInfo(render, info);

        if (info == null || info.getContext(ArchaicRenderSectionBuiltInfo.GLOBAL_BLOCK_ENTITIES).isEmpty()) {
            this.sectionsWithGlobalEntities.remove(render);
        } else {
            this.sectionsWithGlobalEntities.add(render);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        this.sectionsWithGlobalEntities.clear();
    }

    public Collection<RenderSection> getSectionsWithGlobalEntities() {
        return ReferenceSets.unmodifiable(this.sectionsWithGlobalEntities);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected @Nullable SectionTicker createSectionTicker() {
        return new GenericSectionSpriteTicker<>((ContextBundle.Key<RenderSection, List<TextureAtlasSprite>>)(Object)ArchaicRenderSectionBuiltInfo.ANIMATED_SPRITES, SpriteUtil::markSpriteActive);
    }
}
