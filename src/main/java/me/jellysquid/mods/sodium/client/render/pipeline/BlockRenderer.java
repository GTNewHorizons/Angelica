package me.jellysquid.mods.sodium.client.render.pipeline;

import com.gtnewhorizons.angelica.compat.forge.ForgeBlockRenderer;
import com.gtnewhorizons.angelica.compat.mojang.BlockColorProvider;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.compat.nd.RecyclingList;
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
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

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

    private final Flags FLAGS = new Flags(true, true, true, false);
    private final RecyclingList<Quad> quadBuf = new RecyclingList<>(Quad::new);

    public BlockRenderer(Minecraft client, LightPipelineProvider lighters, BiomeColorBlender biomeColorBlender) {
        // TODO: Sodium - Block Colors
        this.blockColors = (BlockColorsExtended) null; //client.getBlockColors();
        this.biomeColorBlender = biomeColorBlender;

        this.lighters = lighters;

        this.occlusionCache = new BlockOcclusionCache();
        // TODO: Sodium - AO Setting
        this.useAmbientOcclusion = Minecraft.getMinecraft().gameSettings.ambientOcclusion > 0;
    }

    public boolean renderModel(BlockRenderView world, Tessellator tessellator, RenderBlocks renderBlocks, BlockState state, BlockPos pos, ChunkModelBuffers buffers, boolean cull, long seed) {
        final LightMode mode = this.getLightingMode(state, world, pos);
        final LightPipeline lighter = this.lighters.getLighter(mode);
        Vector3d offset = state.getModelOffset(world, pos);

        boolean rendered = false;

//        modelData = model.getModelData(world, pos, state, modelData);
//
//        if(ForgeBlockRenderer.useForgeLightingPipeline()) {
//            MatrixStack mStack;
//            if(!offset.equals(ZERO)) {
//                mStack = new MatrixStack();
//                mStack.translate(offset.x, offset.y, offset.z);
//            } else
//                mStack = EMPTY_STACK;
//            final SinkingVertexBuilder builder = SinkingVertexBuilder.getInstance();
//            builder.reset();
//            rendered = forgeBlockRenderer.renderBlock(mode, state, pos, world, model, mStack, builder, random, seed, modelData, cull, this.occlusionCache, buffers.getRenderData());
//            builder.flush(buffers);
//            return rendered;
//        }
        final Block block = state.getBlock();
        // TODO: Occlusion by side... needs to break apart or invasively modify renderBlockByRenderType
        // Or figure out the facing of the quad...
        rendered = renderBlocks.renderBlockByRenderType(block, pos.x, pos.y, pos.z);

//        for (ForgeDirection dir : DirectionUtil.ALL_DIRECTIONS) {
//            this.random.setSeed(seed);
//
//            List<BakedQuad> sided = model.getQuads(state, dir, this.random, modelData);
//
//            if (sided.isEmpty()) {
//                continue;
//            }
//
//            if (!cull || this.occlusionCache.shouldDrawSide(state, world, pos, dir)) {
//                this.renderQuadList(world, state, pos, lighter, offset, buffers, sided, dir);
//
//                rendered = true;
//            }
//        }

        this.random.setSeed(seed);
        List<Quad> all = tesselatorToBakedQuadList(tessellator, pos);


//        List<BakedQuad> all = model.getQuads(state, null, this.random, modelData);

        if (!all.isEmpty()) {
            this.renderQuadList(world, state, pos, lighter, offset, buffers, all, null);

            rendered = true;
        }

        return rendered;
    }

    private int tesselatorDataCount;
    private List<Quad> tesselatorToBakedQuadList(Tessellator t, BlockPos pos) {
        // Temporarily borrowed/badly adapted from Neodymium
        tesselatorDataCount++;

//        List<String> errors = new ArrayList<>();
//        List<String> warnings = new ArrayList<>();
//        if(t.drawMode != GL11.GL_QUADS && t.drawMode != GL11.GL_TRIANGLES) {
//            errors.add("Unsupported draw mode: " + t.drawMode);
//        }
//        if(!t.hasTexture) {
//            errors.add("Texture data is missing.");
//        }
//        if(!t.hasBrightness) {
//            warnings.add("Brightness data is missing");
//        }
//        if(!t.hasColor) {
//            warnings.add("Color data is missing");
//        }
//        if(t.hasNormals && GL11.glIsEnabled(GL11.GL_LIGHTING)) {
//            errors.add("Chunk uses GL lighting, this is not implemented.");
//        }
        FLAGS.hasBrightness = t.hasBrightness;
        FLAGS.hasColor = t.hasColor;

        int verticesPerPrimitive = t.drawMode == GL11.GL_QUADS ? 4 : 3;

        for(int quadI = 0; quadI < t.vertexCount / verticesPerPrimitive; quadI++) {
            Quad quad = quadBuf.next();
            // RenderBlocks adds the subchunk-relative coordinates as the offset, cancel it out here
            quad.setState(t.rawBuffer, quadI * (verticesPerPrimitive * 8), FLAGS, t.drawMode, -pos.x, -pos.y, -pos.z);
            if(quad.deleted) {
                quadBuf.remove();
            }
        }
//        final boolean silenceErrors = false;
//
//        if(!quadBuf.isEmpty() && (!errors.isEmpty() || !warnings.isEmpty()) && /*!Config.silenceErrors*/!silenceErrors) {
//            for(String error : errors) {
//                LOGGER.error("Error: " + error);
//            }
//            for(String warning : warnings) {
//                LOGGER.error("Warning: " + warning);
//            }
//            LOGGER.error("(Tessellator pos: ({}, {}, {}), Tessellation count: {}", t.xOffset, t.yOffset, t.zOffset, tesselatorDataCount);
//            LOGGER.error("Stack trace:");
//            try {
//                // Generate a stack trace
//                throw new IllegalArgumentException();
//            } catch(IllegalArgumentException e) {
//                e.printStackTrace();
//            }
//        }
        final List<Quad> quads = quadBuf.getAsList();
        quadBuf.reset();
        return quads;
    }

    private void renderQuadList(BlockRenderView world, BlockState state, BlockPos pos, LightPipeline lighter, Vector3d offset,
                                ChunkModelBuffers buffers, List<Quad> quads, ForgeDirection cullFace) {
    	ModelQuadFacing facing = cullFace == null ? ModelQuadFacing.UNASSIGNED : ModelQuadFacing.fromDirection(cullFace);
        BlockColorProvider colorizer = null;

        ModelVertexSink sink = buffers.getSink(facing);
        sink.ensureCapacity(quads.size() * 4);

        ChunkRenderData.Builder renderData = buffers.getRenderData();

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            Quad quad = quads.get(i);

            QuadLightData light = this.cachedQuadLightData;
            lighter.calculate((ModelQuadView) quad, pos, light, cullFace, quad.getFace(), quad.hasShade());

            // TODO: Sodium - BlockColors
            if (quad.hasColor() && colorizer == null && this.blockColors != null) {
                colorizer = this.blockColors.getColorProvider(state);
            }

            this.renderQuad(world, state, pos, sink, offset, colorizer, quad, light, renderData);
        }

        sink.flush();
    }

    private void renderQuad(BlockRenderView world, BlockState state, BlockPos pos, ModelVertexSink sink, Vector3d offset,
                            BlockColorProvider colorProvider, Quad quad, QuadLightData light, ChunkRenderData.Builder renderData) {

        ModelQuadOrientation order = ModelQuadOrientation.orient(light.br);

        int[] colors = null;

        if (quad.hasColor()) {
            colors = this.biomeColorBlender.getColors(colorProvider, world, state, pos, quad);
        }

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = order.getVertexIndex(dstIndex);

            float x = quad.getX(srcIndex) + (float) offset.x;
            float y = quad.getY(srcIndex) + (float) offset.y;
            float z = quad.getZ(srcIndex) + (float) offset.z;

            int color = ColorABGR.mul(colors != null ? colors[srcIndex] : quad.getColor(srcIndex), light.br[srcIndex]);

            float u = quad.getTexU(srcIndex);
            float v = quad.getTexV(srcIndex);

            int lm = ModelQuadUtil.mergeBakedLight(quad.getLight(srcIndex), light.lm[srcIndex]);

            sink.writeQuad(x, y, z, color, u, v, lm);
        }

        TextureAtlasSprite sprite = quad.rubidium$getSprite();

        if (sprite != null) {
            renderData.addSprite(sprite);
        }
    }

    private LightMode getLightingMode(BlockState state, BlockRenderView world, BlockPos pos) {
        // TODO: Sodium: Ambient Occlusion
        final Block block = state.getBlock();
        if (this.useAmbientOcclusion && block.getLightValue() == 0 && /*model.isAmbientOcclusion(state) &&*/ state.getLightValue(world, pos) == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
    }

    public static class Flags {
        boolean hasTexture;
        public boolean hasBrightness;
        public boolean hasColor;
        boolean hasNormals;

        public Flags(byte flags) {
            hasTexture = (flags & 1) != 0;
            hasBrightness = (flags & 2) != 0;
            hasColor = (flags & 4) != 0;
            hasNormals = (flags & 8) != 0;
        }

        public Flags(boolean hasTexture, boolean hasBrightness, boolean hasColor, boolean hasNormals) {
            this.hasTexture = hasTexture;
            this.hasBrightness = hasBrightness;
            this.hasColor = hasColor;
            this.hasNormals = hasNormals;
        }

        public byte toByte() {
            byte flags = 0;
            if(hasTexture) {
                flags |= 1;
            }
            if(hasBrightness) {
                flags |= 2;
            }
            if(hasColor) {
                flags |= 4;
            }
            if(hasNormals) {
                flags |= 8;
            }
            return flags;
        }
    }
}
