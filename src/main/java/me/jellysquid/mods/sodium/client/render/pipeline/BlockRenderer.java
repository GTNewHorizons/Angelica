package me.jellysquid.mods.sodium.client.render.pipeline;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.compat.nd.RecyclingList;
import com.gtnewhorizons.angelica.mixins.interfaces.ITessellatorInstance;
import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.IBlockAccess;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public class BlockRenderer {
    public static Vector3d ZERO = new Vector3d(0, 0, 0);
    private static final MatrixStack EMPTY_STACK = new MatrixStack();

    private final Random random = new XoRoShiRoRandom();

    private final QuadLightData cachedQuadLightData = new QuadLightData();

    private final boolean useAmbientOcclusion;

    private final Flags FLAGS = new Flags(true, true, true, false);
    private final RecyclingList<Quad> quadBuf = new RecyclingList<>(Quad::new);

    public BlockRenderer(Minecraft client) {
        // TODO: Sodium - AO Setting
        this.useAmbientOcclusion = Minecraft.getMinecraft().gameSettings.ambientOcclusion > 0;
    }

    public boolean renderModel(IBlockAccess world, Tessellator tessellator, RenderBlocks renderBlocks, Block block, int meta, BlockPos pos, ChunkModelBuffers buffers, boolean cull, long seed) {

        boolean rendered = false;

//        modelData = model.getModelData(world, pos, state, modelData);

        tessellator.startDrawingQuads();
        renderBlocks.renderBlockByRenderType(block, pos.x, pos.y, pos.z);

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

        final List<Quad> all = tesselatorToBakedQuadList(tessellator, pos);

        for(ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            this.renderQuadList(pos, buffers, all, facing);
        }

        if(!all.isEmpty())
            rendered = true;

        ((ITessellatorInstance) tessellator).discard();

        return rendered;
    }

    private int tesselatorDataCount;
    private List<Quad> tesselatorToBakedQuadList(Tessellator t, BlockPos pos) {
        // Adapted from Neodymium
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

    private void renderQuadList(BlockPos pos,
                                ChunkModelBuffers buffers, List<Quad> quads, ModelQuadFacing facing) {

        ModelVertexSink sink = buffers.getSink(facing);
        sink.ensureCapacity(quads.size() * 4);

        ChunkRenderData.Builder renderData = buffers.getRenderData();

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            Quad quad = quads.get(i);

            if(quad.normal != facing)
                continue;

            this.renderQuad(sink, quad, renderData);
        }

        sink.flush();
    }

    private void renderQuad(ModelVertexSink sink,
                            Quad quad, ChunkRenderData.Builder renderData) {

        // TODO reorder using packed light
        ModelQuadOrientation order = ModelQuadOrientation.NORMAL;

        int[] colors = null;

        if (quad.hasColor()) {
            colors = quad.getColors();
        }

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = order.getVertexIndex(dstIndex);

            float x = quad.getX(srcIndex);
            float y = quad.getY(srcIndex);
            float z = quad.getZ(srcIndex);

            int color = quad.getColor(srcIndex);

            float u = quad.getTexU(srcIndex);
            float v = quad.getTexV(srcIndex);

            int lm = quad.getLight(srcIndex);

            sink.writeQuad(x, y, z, color, u, v, lm);
        }

        TextureAtlasSprite sprite = quad.rubidium$getSprite();

        if (sprite != null) {
            renderData.addSprite(sprite);
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
