package me.jellysquid.mods.sodium.client.render.pipeline;

import com.gtnewhorizons.angelica.compat.forge.ForgeBlockRenderer;
import com.gtnewhorizons.angelica.compat.forge.IModelData;
import com.gtnewhorizons.angelica.compat.forge.SinkingVertexBuilder;
import com.gtnewhorizons.angelica.compat.mojang.BakedModel;
import com.gtnewhorizons.angelica.compat.mojang.BakedQuad;
import com.gtnewhorizons.angelica.compat.mojang.BlockColorProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlender;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import me.jellysquid.mods.sodium.client.world.biome.BlockColorsExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3d;

import java.util.List;
import java.util.Random;

public class BlockRenderer {
    public static Vector3d ZERO = new Vector3d(0, 0, 0);
    private static final MatrixStack EMPTY_STACK = new MatrixStack();

    private final Random random = new XoRoShiRoRandom();

    private final BlockColorsExtended blockColors;
    private final BlockOcclusionCache occlusionCache;

    private final QuadLightData cachedQuadLightData = new QuadLightData();

    private final ForgeBlockRenderer forgeBlockRenderer = new ForgeBlockRenderer();

    private final BiomeColorBlender biomeColorBlender;
    private final LightPipelineProvider lighters;

    private final boolean useAmbientOcclusion;

    public BlockRenderer(Minecraft client, LightPipelineProvider lighters, BiomeColorBlender biomeColorBlender) {
        // TODO: Sodium - Block Colors
        this.blockColors = (BlockColorsExtended) null; //client.getBlockColors();
        this.biomeColorBlender = biomeColorBlender;

        this.lighters = lighters;

        this.occlusionCache = new BlockOcclusionCache();
        // TODO: Sodium - AO Setting
        this.useAmbientOcclusion = Minecraft.getMinecraft().gameSettings.ambientOcclusion > 0;
    }

    public boolean renderModel(BlockRenderView world, BlockState state, BlockPos pos, BakedModel model, ChunkModelBuffers buffers, boolean cull, long seed, IModelData modelData) {
        LightMode mode = this.getLightingMode(state, model, world, pos);
        LightPipeline lighter = this.lighters.getLighter(mode);
        Vector3d offset = state.getModelOffset(world, pos);

        boolean rendered = false;

        modelData = model.getModelData(world, pos, state, modelData);

        if(ForgeBlockRenderer.useForgeLightingPipeline()) {
            MatrixStack mStack;
            if(!offset.equals(ZERO)) {
                mStack = new MatrixStack();
                mStack.translate(offset.x, offset.y, offset.z);
            } else
                mStack = EMPTY_STACK;
            final SinkingVertexBuilder builder = SinkingVertexBuilder.getInstance();
            builder.reset();
            rendered = forgeBlockRenderer.renderBlock(mode, state, pos, world, model, mStack, builder, random, seed, modelData, cull, this.occlusionCache, buffers.getRenderData());
            builder.flush(buffers);
            return rendered;
        }

        for (ForgeDirection dir : DirectionUtil.ALL_DIRECTIONS) {
            this.random.setSeed(seed);

            List<BakedQuad> sided = model.getQuads(state, dir, this.random, modelData);

            if (sided.isEmpty()) {
                continue;
            }

            if (!cull || this.occlusionCache.shouldDrawSide(state, world, pos, dir)) {
                this.renderQuadList(world, state, pos, lighter, offset, buffers, sided, dir);

                rendered = true;
            }
        }

        this.random.setSeed(seed);

        List<BakedQuad> all = model.getQuads(state, null, this.random, modelData);

        if (!all.isEmpty()) {
            this.renderQuadList(world, state, pos, lighter, offset, buffers, all, null);

            rendered = true;
        }

        return rendered;
    }

    private void renderQuadList(BlockRenderView world, BlockState state, BlockPos pos, LightPipeline lighter, Vector3d offset,
                                ChunkModelBuffers buffers, List<BakedQuad> quads, ForgeDirection cullFace) {
    	ModelQuadFacing facing = cullFace == null ? ModelQuadFacing.UNASSIGNED : ModelQuadFacing.fromDirection(cullFace);
        BlockColorProvider colorizer = null;

        ModelVertexSink sink = buffers.getSink(facing);
        sink.ensureCapacity(quads.size() * 4);

        ChunkRenderData.Builder renderData = buffers.getRenderData();

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            BakedQuad quad = quads.get(i);

            QuadLightData light = this.cachedQuadLightData;
            lighter.calculate((ModelQuadView) quad, pos, light, cullFace, quad.getFace(), quad.hasShade());

            if (quad.hasColor() && colorizer == null) {
                colorizer = this.blockColors.getColorProvider(state);
            }

            this.renderQuad(world, state, pos, sink, offset, colorizer, quad, light, renderData);
        }

        sink.flush();
    }

    private void renderQuad(BlockRenderView world, BlockState state, BlockPos pos, ModelVertexSink sink, Vector3d offset,
                            BlockColorProvider colorProvider, BakedQuad bakedQuad, QuadLightData light, ChunkRenderData.Builder renderData) {
        ModelQuadView src = (ModelQuadView) bakedQuad;

        ModelQuadOrientation order = ModelQuadOrientation.orient(light.br);

        int[] colors = null;

        if (bakedQuad.hasColor()) {
            colors = this.biomeColorBlender.getColors(colorProvider, world, state, pos, src);
        }

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = order.getVertexIndex(dstIndex);

            float x = src.getX(srcIndex) + (float) offset.x;
            float y = src.getY(srcIndex) + (float) offset.y;
            float z = src.getZ(srcIndex) + (float) offset.z;

            int color = ColorABGR.mul(colors != null ? colors[srcIndex] : src.getColor(srcIndex), light.br[srcIndex]);

            float u = src.getTexU(srcIndex);
            float v = src.getTexV(srcIndex);

            int lm = ModelQuadUtil.mergeBakedLight(src.getLight(srcIndex), light.lm[srcIndex]);

            sink.writeQuad(x, y, z, color, u, v, lm);
        }

        TextureAtlasSprite sprite = src.rubidium$getSprite();

        if (sprite != null) {
            renderData.addSprite(sprite);
        }
    }

    private LightMode getLightingMode(BlockState state, BakedModel model, BlockRenderView world, BlockPos pos) {
        if (this.useAmbientOcclusion && model.isAmbientOcclusion(state) && state.getLightValue(world, pos) == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
    }
}
