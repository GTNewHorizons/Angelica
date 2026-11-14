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

import java.util.Map;

/**
 * For mods that bake block geometry outside the chunk renderer (moving structures, in-world previews) and want it to
 * look like terrain, shaderpacks included.
 * <p>
 * Under a shaderpack terrain gets the pack's terrain programs, its block.properties materials (mc_Entity) and the
 * pack's own AO and directional shading. Draw between {@link #beginTerrainDraw} and {@link #endTerrainDraw} to get the
 * terrain programs, and use the getters here to bake the way the pack wants. Without a shaderpack nothing changes.
 */
public final class TerrainRendering {

    private static int terrainDrawDepth;
    private static boolean terrainDrawPushed;

    private TerrainRendering() {}

    /**
     * Draws until {@link #endTerrainDraw} use the pack's terrain program instead of the one for the current phase
     * (entities, block entities). Main thread, end it in a finally block. Nests, the outermost call picks the program.
     *
     * @param translucent true for render pass 1 geometry (glass, water), false for solid and cutout
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

    /** Internal, start of each frame. Ends a terrain draw that never got ended (e.g. the caller threw). */
    public static void resetForFrame() {
        if (terrainDrawDepth == 0) return;
        AngelicaMod.LOGGER.warn("A terrain draw was never ended, ending it");
        if (terrainDrawPushed) GbufferPrograms.popOverridePhase();
        terrainDrawPushed = false;
        terrainDrawDepth = 0;
    }

    /**
     * True when the pack wants AO in the vertex color's alpha instead of multiplied into the color, so it can apply it
     * itself. Put the AO from {@link TerrainLighter} in alpha when this is set.
     */
    public static boolean usesSeparateAo() {
        return ShaderPacks.isInUse() && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo();
    }

    /**
     * True when the chunk mesher runs its own light pipeline over terrain, the same one {@link TerrainLighter} runs. It
     * only relights the faces {@link #isSmoothLit} marks (and fluids under a pack), everything else keeps the light it
     * was baked with. AO goes in the color's alpha when {@link #usesSeparateAo}, multiplied into the color otherwise.
     */
    public static boolean usesTerrainLighting() {
        return ShaderPacks.isInUse() || BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()
            || ClientProxy.options().quality.useCeleritasSmoothLighting;
    }

    /** True when the pack does its own directional shading, so terrain is baked without vanilla's per-face shading. */
    public static boolean disablesDirectionalShading() {
        return ShaderPacks.isInUse() && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
    }

    /**
     * The Forge render pass the pack moves this block to (e.g. {@code layer.translucent} in block.properties), or -1 if
     * it doesn't move it or there's no pack. The chunk mesher only draws a moved block in that pass, so do the same.
     */
    public static int getRenderPassOverride(Block block) {
        if (!ShaderPacks.isInUse()) return -1;
        final Map<Block, BlockRenderLayer> overrides = BlockRenderingSettings.INSTANCE.getBlockTypeIds();
        if (overrides == null) return -1;
        final BlockRenderLayer layer = overrides.get(block);
        return layer == null ? -1 : layer.toVanillaPass();
    }

    /** The pack's block.properties id for this block, -1 if it's unmapped or there's no pack. */
    public static int getBlockMaterialId(Block block, int metadata) {
        return ShaderPacks.isInUse() ? BlockMaterialAttribute.blockMaterialId(block, metadata) : -1;
    }

    /**
     * The id the chunk mesher gives this block where it stands, which isn't always the plain lookup. A fluid with no
     * entry uses its still or flowing twin's, grass under snow gets the pack's snowy entry and the top half of a double
     * plant is looked up as the plant its bottom half says. -1 if it's unmapped or there's no pack.
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

    /**
     * What the chunk mesher counts as a fluid, water or lava material. Under a pack it lights fluid quads with its
     * light pipeline but leaves out the AO, pass that on to {@link TerrainLighter#light}.
     */
    public static boolean isFluid(Block block) {
        final Material material = block.getMaterial();
        return material == Material.water || material == Material.lava;
    }

    /**
     * While on, RenderBlocks draws into this tessellator like it does for the chunk mesher. Pass the one it draws into
     * ({@code Tessellator.instance} on the main thread). Standard blocks that would take vanilla's smooth lighting path
     * are drawn flat instead, with no AO or face shading, and their vertices get marked (see {@link #isSmoothLit}).
     * Light those with {@link TerrainLighter} and leave the rest alone, that's all the mesher does. Only kicks in when
     * the mesher would do it too: smooth lighting with Celeritas smooth lighting on, or a pack using separate AO. Turn
     * it off again in a finally block.
     */
    public static void setMeshing(Tessellator tessellator, boolean meshing) {
        ((StateAwareTessellator) tessellator).angelica$setCeleritasMeshing(meshing);
    }

    /**
     * True while the vertices being added are from a face the chunk mesher would relight (see {@link #setMeshing}),
     * vanilla's smooth lighting path or a renderer that asked for the same. Check it per vertex as they're added.
     */
    public static boolean isSmoothLit(Tessellator tessellator) {
        return ((StateAwareTessellator) tessellator).angelica$isAppliedAo();
    }

    /** True while the vertices being added are from a renderer that wants no directional shading on them. */
    public static boolean hasNoDirectionalShading(Tessellator tessellator) {
        return ((StateAwareTessellator) tessellator).angelica$isNoDirectionalShading();
    }

    /**
     * Vertex attribute location the pack's programs read mc_Entity from. Bake two shorts per vertex here, the material
     * id and 0 (1 for fluids, like the mesher). Only bind it while a shaderpack is in use.
     */
    public static int getMaterialAttributeLocation() {
        return ProgramCreator.MC_ENTITY;
    }

    /**
     * Vertex attribute location the pack's programs read mc_midTexCoord from, the middle of the vertex's sprite. Packs
     * use it for sprite bounds (e.g. POM). Two floats per vertex. Only bind it while a shaderpack is in use, and
     * between {@link #beginOwnExtendedAttributes} and {@link #endOwnExtendedAttributes}.
     */
    public static int getMidTexCoordAttributeLocation() {
        return ProgramCreator.MC_MID_TEX_COORD;
    }

    /**
     * Vertex attribute location the pack's programs read at_tangent from. Four floats per vertex, xyz plus w for the
     * handedness. Same rules as {@link #getMidTexCoordAttributeLocation}.
     */
    public static int getTangentAttributeLocation() {
        return ProgramCreator.AT_TANGENT;
    }

    /**
     * Angelica fills in mc_midTexCoord and at_tangent itself for client array draws out of a VBO, guessing from the
     * buffer layout, and binds that over whatever the draw set. Draws until {@link #endOwnExtendedAttributes} bring
     * their own and are left alone. Nests, main thread.
     */
    public static void beginOwnExtendedAttributes() {
        FfpExtendedAttribs.beginInternalDraw();
    }

    public static void endOwnExtendedAttributes() {
        FfpExtendedAttribs.endInternalDraw();
    }

    /**
     * Angelica only animates sprites it saw drawn this frame, and geometry baked once and drawn from a buffer never
     * goes through the tessellator again. This records the sprites used on this thread until it's closed.
     * Call {@link SpriteCapture#markUsed} each frame the baked geometry is drawn. Close it on the same thread, in a
     * finally block.
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

        /** Marks the recorded sprites as drawn this frame so they keep animating. */
        public void markUsed() {
            capture.markUsed();
        }

        /** Stops recording. The sprites recorded so far stay. */
        @Override
        public void close() {
            capture.close();
        }
    }

}
