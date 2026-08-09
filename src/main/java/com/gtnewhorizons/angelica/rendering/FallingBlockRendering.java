package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

public final class FallingBlockRendering {

    public static boolean active;

    private static final FallingBlockMetaAccess META_ACCESS = new FallingBlockMetaAccess();

    private FallingBlockRendering() {
    }

    public static FallingBlockMetaAccess metaAccess(IBlockAccess world, int x, int y, int z, int metadata) {
        return META_ACCESS.set(world, x, y, z, metadata);
    }

    public static void setEntityAttribute(Block block, int metadata) {
        if (!shadersActive()) return;
        GLStateManager.glVertexAttrib2s(ProgramCreator.MC_ENTITY, (short) blockMaterialId(block, metadata), (short) 0);
    }

    public static void resetEntityAttribute() {
        if (!shadersActive()) return;
        GLStateManager.glVertexAttrib2s(ProgramCreator.MC_ENTITY, (short) -1, (short) -1);
    }

    private static int blockMaterialId(Block block, int metadata) {
        final Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
        if (blockMetaMatches == null) return -1;

        final Int2IntMap metaMap = blockMetaMatches.get(block);
        return metaMap != null ? BlockMaterialMapping.resolveId(metaMap, metadata) : -1;
    }

    public static boolean isActive() {
        return active && shadersActive();
    }

    private static boolean shadersActive() {
        return TessellatorManager.isOnMainThread() && IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean skipDirectionalShading() {
        return isActive() && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
    }
}
