package com.gtnewhorizons.angelica.api.terrain;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.api.shaders.ShaderPacks;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.BlockMaterialAttribute;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import com.prupe.mcpatcher.mal.block.RenderBlocksUtils;
import me.jellysquid.mods.sodium.client.gui.options.named.MipmapGeneration;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockStaticLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;

import java.util.Map;

/**
 * For mods that bake block geometry outside the chunk renderer and want it to match terrain, including under a
 * shaderpack (terrain programs, block.properties materials, the pack's AO and directional shading).
 * <p>
 * Begin/end pairs nest and are render thread only. Call the end in a finally block.
 */
public final class TerrainRendering {

    private static int terrainDrawDepth;
    private static boolean terrainDrawPushed;

    private TerrainRendering() {}

    /**
     * Uses the pack's terrain program until {@link #endTerrainDraw} instead of the current phase's. The outermost call
     * picks the program. No-op without a shaderpack.
     *
     * @param translucent true for render pass 1, false for solid and cutout
     */
    public static void beginTerrainDraw(boolean translucent) {
        if (terrainDrawDepth++ > 0) return;
        terrainDrawPushed = ShaderPacks.isInUse();
        if (terrainDrawPushed) {
            GbufferPrograms.pushOverridePhase(translucent ? WorldRenderingPhase.TERRAIN_TRANSLUCENT : WorldRenderingPhase.TERRAIN_CUTOUT);
        }
    }

    public static void endTerrainDraw() {
        if (terrainDrawDepth == 0) return;
        if (--terrainDrawDepth > 0) return;
        if (terrainDrawPushed) GbufferPrograms.popOverridePhase();
        terrainDrawPushed = false;
    }

    /** Called at the start of each frame. Closes a terrain draw that was never ended. */
    public static void resetForFrame() {
        if (terrainDrawDepth == 0) return;
        AngelicaMod.LOGGER.warn("A terrain draw was never ended, ending it");
        if (terrainDrawPushed) GbufferPrograms.popOverridePhase();
        terrainDrawPushed = false;
        terrainDrawDepth = 0;
    }

    /** True when the pack wants AO in vertex color alpha instead of multiplied into the color */
    public static boolean usesSeparateAo() {
        return ShaderPacks.isInUse() && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo();
    }

    /**
     * True when the chunk mesher relights terrain with the pipeline {@link TerrainLighter} uses. Only faces marked by
     * {@link #isSmoothLit} (and fluids under a pack) are relit, the rest keep their baked light.
     */
    public static boolean usesTerrainLighting() {
        return ShaderPacks.isInUse() || BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()
            || ClientProxy.options().quality.useCeleritasSmoothLighting;
    }

    /**
     * Alpha cutoff terrain uses for a render pass without a shaderpack. Pass 0 is 0.5 (vanilla uses 0.1), pass 1
     * follows the mipmap setting.
     */
    public static float getAlphaCutoff(int pass) {
        return pass == 0 ? AlphaCutoffParameter.HALF.cutoff() : MipmapGeneration.alphaFloorCutoff().cutoff();
    }

    /** True when the pack does its own directional shading, so terrain is baked without vanilla's face shading */
    public static boolean disablesDirectionalShading() {
        return ShaderPacks.isInUse() && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
    }

    /**
     * Forge render pass the pack moves this block to (e.g. {@code layer.translucent}), -1 if not moved or no pack. The
     * mesher only draws a moved block in that pass.
     */
    public static int getRenderPassOverride(Block block) {
        if (!ShaderPacks.isInUse()) return -1;
        final Map<Block, BlockRenderLayer> overrides = BlockRenderingSettings.INSTANCE.getBlockTypeIds();
        if (overrides == null) return -1;
        final BlockRenderLayer layer = overrides.get(block);
        return layer == null ? -1 : layer.toVanillaPass();
    }

    /** The pack's block.properties id, -1 if unmapped or no pack */
    public static int getBlockMaterialId(Block block, int metadata) {
        return ShaderPacks.isInUse() ? BlockMaterialAttribute.blockMaterialId(block, metadata) : -1;
    }

    /**
     * The block.properties id the mesher gives this block at this position. Unlike the plain lookup it falls back to a
     * fluid's still or flowing twin, applies snowy entries and resolves double plant tops. -1 if unmapped or no pack.
     */
    public static int getBlockMaterialId(IBlockAccess world, Block block, int metadata, int x, int y, int z) {
        if (!ShaderPacks.isInUse()) return -1;
        if (isFluid(block)) {
            int id = BlockMaterialAttribute.blockMaterialId(block, metadata);
            if (id == -1) {
                Block twin = null;
                if (block instanceof BlockStaticLiquid) twin = Block.getBlockById(Block.getIdFromBlock(block) - 1);
                else if (block instanceof BlockDynamicLiquid) twin = Block.getBlockById(Block.getIdFromBlock(block) + 1);
                if (twin != null) id = BlockMaterialAttribute.blockMaterialId(twin, metadata);
            }
            return id;
        }
        int effectiveMeta = metadata;
        if (BlockRenderingSettings.INSTANCE.hasSnowyEntries()
            && BlockRenderingSettings.INSTANCE.getSnowyBlocks().contains(block)
            && RenderBlocksUtils.isSnowCovered(world, x, y, z)) {
            effectiveMeta |= BlockMaterialMapping.SNOWY_META_BIT;
        } else if (block instanceof BlockDoublePlant doublePlant && BlockDoublePlant.func_149887_c(metadata)) {
            effectiveMeta = 0x8 | (doublePlant.func_149885_e(world, x, y, z) & 7);
        }
        return BlockMaterialAttribute.blockMaterialId(block, effectiveMeta);
    }

    /** Water or lava material, what the mesher treats as a fluid for lighting and material ids */
    public static boolean isFluid(Block block) {
        final Material material = block.getMaterial();
        return material == Material.water || material == Material.lava;
    }

    /**
     * Makes RenderBlocks draw into this tessellator like it does for the chunk mesher. Blocks that would take vanilla's
     * smooth lighting path are drawn flat and marked (see {@link #isSmoothLit}) so they can be relit with
     * {@link TerrainLighter}. Only applies when {@link #usesTerrainLighting} would. Turn it off in a finally block.
     */
    public static void setMeshing(Tessellator tessellator, boolean meshing) {
        ((StateAwareTessellator) tessellator).angelica$setCeleritasMeshing(meshing);
    }

    /** True while the vertices being added are from a face the mesher would relight (see {@link #setMeshing}) */
    public static boolean isSmoothLit(Tessellator tessellator) {
        return ((StateAwareTessellator) tessellator).angelica$isAppliedAo();
    }

    /** True while the vertices being added should get no directional shading */
    public static boolean hasNoDirectionalShading(Tessellator tessellator) {
        return ((StateAwareTessellator) tessellator).angelica$isNoDirectionalShading();
    }

    /**
     * Attribute location of mc_Entity. Two shorts per vertex, the material id and 0 (1 for fluids). Only bind it while a
     * shaderpack is in use.
     */
    public static int getMaterialAttributeLocation() {
        return ProgramCreator.MC_ENTITY;
    }

    /**
     * Attribute location of mc_midTexCoord, the centre of the vertex's sprite. Two floats per vertex. Only bind it while
     * a shaderpack is in use, between {@link #beginOwnExtendedAttributes} and {@link #endOwnExtendedAttributes}.
     */
    public static int getMidTexCoordAttributeLocation() {
        return ProgramCreator.MC_MID_TEX_COORD;
    }

    /** Attribute location of at_tangent, four floats per vertex (w is handedness). Same rules as mc_midTexCoord. */
    public static int getTangentAttributeLocation() {
        return ProgramCreator.AT_TANGENT;
    }

    /**
     * Stops Angelica generating mc_midTexCoord and at_tangent for draws until {@link #endOwnExtendedAttributes}, for
     * draws that supply their own.
     */
    public static void beginOwnExtendedAttributes() {
        FfpExtendedAttribs.beginInternalDraw();
    }

    public static void endOwnExtendedAttributes() {
        FfpExtendedAttribs.endInternalDraw();
    }

    /**
     * Records the sprites used on this thread until closed. Angelica only animates sprites drawn this frame, so call
     * {@link SpriteCapture#markUsed} each frame the baked geometry is drawn. Close on the same thread.
     */
    public static SpriteCapture captureSprites() {
        return new SpriteCapture(AnimationsRenderUtils.captureSprites());
    }

    /** See {@link #captureSprites}. */
    public static final class SpriteCapture implements AutoCloseable {

        private final AnimationsRenderUtils.SpriteCapture capture;

        private SpriteCapture(AnimationsRenderUtils.SpriteCapture capture) {
            this.capture = capture;
        }

        /** Marks the recorded sprites as drawn this frame */
        public void markUsed() {
            capture.markUsed();
        }

        /** Stops recording, keeping what was recorded */
        @Override
        public void close() {
            capture.close();
        }
    }

}
