package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.iris.IrisDisplayListState;
import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.block.Block;

/** Gives blocks drawn outside chunk meshing their block.properties id on mc_Entity. */
public final class BlockMaterialAttribute {

    private BlockMaterialAttribute() {
    }

    public static void set(Block block, int metadata) {
        IrisDisplayListState.recordBlockEntityAttribute(block, metadata);
        if (!shadersActive()) return;
        IrisDisplayListState.runUnrecorded(() ->
            GLStateManager.glVertexAttrib2s(ProgramCreator.MC_ENTITY, (short) blockMaterialId(block, metadata), (short) 0));
    }

    public static void reset() {
        IrisDisplayListState.recordBlockEntityAttribute(null, 0);
        if (!shadersActive()) return;
        IrisDisplayListState.runUnrecorded(() ->
            GLStateManager.glVertexAttrib2s(ProgramCreator.MC_ENTITY, (short) -1, (short) -1));
    }

    static boolean shadersActive() {
        return TessellatorManager.isOnMainThread() && IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean skipDirectionalShading() {
        if (!shadersActive() || !BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading()
            || ((StateAwareTessellator) TessellatorManager.get()).angelica$isCeleritasMeshing()) return false;
        return switch (GbufferPrograms.getCurrentPhase()) {
            case TERRAIN_SOLID, TERRAIN_CUTOUT, TERRAIN_CUTOUT_MIPPED, TERRAIN_TRANSLUCENT -> true;
            default -> false;
        };
    }

    public static int blockMaterialId(Block block, int metadata) {
        final Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
        if (blockMetaMatches == null) return -1;

        final Int2IntMap metaMap = blockMetaMatches.get(block);
        return metaMap != null ? BlockMaterialMapping.resolveId(metaMap, metadata) : -1;
    }

    public static WorldRenderingPhase renderingPhase(Block block, boolean fading) {
        if (fading) return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
        if (block == null) return WorldRenderingPhase.TERRAIN_CUTOUT;
        final var overrides = BlockRenderingSettings.INSTANCE.getBlockTypeIds();
        final BlockRenderLayer override = overrides == null ? null : overrides.get(block);
        if (override != null) {
            return switch (override) {
                case SOLID -> WorldRenderingPhase.TERRAIN_SOLID;
                case CUTOUT -> WorldRenderingPhase.TERRAIN_CUTOUT;
                case CUTOUT_MIPPED -> WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
                case TRANSLUCENT -> WorldRenderingPhase.TERRAIN_TRANSLUCENT;
            };
        }
        if (block.getRenderBlockPass() == 1) return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
        return block.isOpaqueCube() ? WorldRenderingPhase.TERRAIN_SOLID : WorldRenderingPhase.TERRAIN_CUTOUT;
    }
}
