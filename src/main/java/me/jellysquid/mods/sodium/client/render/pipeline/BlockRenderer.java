package me.jellysquid.mods.sodium.client.render.pipeline;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.compat.nd.RecyclingList;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
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
import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public class BlockRenderer {

    private final Random random = new XoRoShiRoRandom();

    private final QuadLightData cachedQuadLightData = new QuadLightData();

    private final boolean useAmbientOcclusion;
    private boolean useSeparateAo;

    private final LightPipelineProvider lighters;

    private final Flags FLAGS = new Flags(true, true, true, false);
    private final RecyclingList<Quad> quadBuf = new RecyclingList<>(Quad::new);


    public BlockRenderer(LightPipelineProvider lighters) {
        this.lighters = lighters;
        // TODO: Sodium - AO Setting
        this.useAmbientOcclusion = Minecraft.getMinecraft().gameSettings.ambientOcclusion > 0;
    }

    public boolean renderModel(IBlockAccess world, Tessellator tessellator, RenderBlocks renderBlocks, Block block, int meta, BlockPos pos, ChunkModelBuffers buffers, boolean cull, long seed) {
        final LightMode mode = this.getLightingMode(block);
        final LightPipeline lighter = this.lighters.getLighter(mode);

        this.useSeparateAo = AngelicaConfig.enableIris && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo();

        boolean rendered = false;

        try {
            tessellator.startDrawingQuads();
            renderBlocks.renderBlockByRenderType(block, pos.x, pos.y, pos.z);


            final List<Quad> all = tessellatorToQuadList(tessellator, pos);

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                this.random.setSeed(seed);
                this.renderQuadList(pos, lighter, buffers, all, facing);
            }

            if (!all.isEmpty()) rendered = true;
        } finally {
            ((ITessellatorInstance) tessellator).discard();
        }


        return rendered;
    }

    private int tesselatorDataCount;
    private List<Quad> tessellatorToQuadList(Tessellator t, BlockPos pos) {
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
            final Quad quad = quadBuf.next();
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

    private void renderQuadList(BlockPos pos, LightPipeline lighter, ChunkModelBuffers buffers, List<Quad> quads, ModelQuadFacing facing) {

        final ModelVertexSink sink = buffers.getSink(facing);
        sink.ensureCapacity(quads.size() * 4);
        final ForgeDirection cullFace = ModelQuadFacing.toDirection(facing);

        final ChunkRenderData.Builder renderData = buffers.getRenderData();

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            final Quad quad = quads.get(i);

            if(quad.normal != facing)
                continue;

            final QuadLightData light = this.cachedQuadLightData;

            // TODO: use Sodium pipeline always
            if(this.useSeparateAo) {
                lighter.calculate(quad, pos, light, cullFace, quad.getFace(), quad.hasShade());
            }


            this.renderQuad(sink, quad, light, renderData);
        }

        sink.flush();
    }

    private void renderQuad(ModelVertexSink sink, Quad quad, QuadLightData light, ChunkRenderData.Builder renderData) {

        final ModelQuadOrientation order = useSeparateAo ? ModelQuadOrientation.orient(light.br) : ModelQuadOrientation.NORMAL;

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            final int srcIndex = order.getVertexIndex(dstIndex);

            final float x = quad.getX(srcIndex);
            final float y = quad.getY(srcIndex);
            final float z = quad.getZ(srcIndex);

            int color = quad.getColor(srcIndex);
            final float ao = light.br[srcIndex];
            if (useSeparateAo) {
                color &= 0x00FFFFFF;
                color |= ((int) (ao * 255.0f)) << 24;
            }

            final float u = quad.getTexU(srcIndex);
            final float v = quad.getTexV(srcIndex);

            int quadLight = quad.getLight(srcIndex);
            final int lm = useSeparateAo ? ModelQuadUtil.mergeBakedLight(quadLight, light.lm[srcIndex]) : quadLight;

            sink.writeQuad(x, y, z, color, u, v, lm);
        }

        final TextureAtlasSprite sprite = quad.rubidium$getSprite();

        if (sprite != null) {
            renderData.addSprite(sprite);
        }
    }
    private LightMode getLightingMode(Block block) {
        if (this.useAmbientOcclusion && block.getAmbientOcclusionLightValue() != 1.0F && block.getLightValue() == 0) {
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
