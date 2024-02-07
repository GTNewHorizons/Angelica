package me.jellysquid.mods.sodium.client.render.pipeline;

import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.client.renderer.CapturingTessellator;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import java.util.List;
import java.util.Random;
import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockRenderer {

    private final Random random = new XoRoShiRoRandom();

    private final QuadLightData cachedQuadLightData = new QuadLightData();

    private final boolean useAmbientOcclusion;
    private boolean useSeparateAo;

    private final LightPipelineProvider lighters;
    private final BlockOcclusionCache occlusionCache;

    private final ObjectPooler<Quad> quadPool = new ObjectPooler<>(Quad::new);
    // TODO: Use modern model API, and store them here


    public BlockRenderer(LightPipelineProvider lighters) {
        this.lighters = lighters;
        // TODO: Sodium - AO Setting
        this.useAmbientOcclusion = Minecraft.getMinecraft().gameSettings.ambientOcclusion > 0;
        this.occlusionCache = new BlockOcclusionCache();
    }

    public boolean renderModel(IBlockAccess world, RenderBlocks renderBlocks, Block block, int meta, BlockPos pos, ChunkModelBuffers buffers, boolean cull, long seed) {
        final LightMode mode = this.getLightingMode(block);
        final LightPipeline lighter = this.lighters.getLighter(mode);

        this.useSeparateAo = AngelicaConfig.enableIris && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo();

        boolean rendered = false;

        if (block instanceof QuadProvider qBlock) {

            for (ForgeDirection dir : ForgeDirection.values()) {

                this.random.setSeed(seed);
                List<Quad> quads = qBlock.getQuads(world, pos, block, meta, dir, random, this.quadPool);

                if (quads.isEmpty()) continue;

                if (!cull || this.occlusionCache.shouldDrawSide(block, meta, world, pos, dir)) {

                    this.renderQuadList(pos, lighter, buffers, quads, ModelQuadFacing.fromDirection(dir), true);
                    rendered = true;
                }

                for (Quad q : quads) this.quadPool.releaseInstance(q);
            }
        } else {

            try {
                TessellatorManager.startCapturing();
                final CapturingTessellator tess = (CapturingTessellator) TessellatorManager.get();
                tess.startDrawingQuads();
                // RenderBlocks adds the subchunk-relative coordinates as the offset, cancel it out here

                tess.setOffset(pos);
                renderBlocks.renderBlockByRenderType(block, pos.x, pos.y, pos.z);
                final List<Quad> quads = TessellatorManager.stopCapturingToPooledQuads();
                tess.resetOffset();

                for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                    this.random.setSeed(seed);
                    this.renderQuadList(pos, lighter, buffers, quads, facing, false);
                }

                if (!quads.isEmpty()) rendered = true;
            } finally {
                TessellatorManager.cleanup();
            }
        }

        return rendered;
    }

    private void renderQuadList(BlockPos pos, LightPipeline lighter, ChunkModelBuffers buffers, List<Quad> quads, ModelQuadFacing facing, boolean useSodiumLight) {

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

            if (useSodiumLight || this.useSeparateAo)
                lighter.calculate(quad, pos, light, cullFace, quad.getCoercedFace(), quad.hasShade());

            this.renderQuad(sink, quad, light, renderData, useSodiumLight);
        }

        sink.flush();
    }

    private void renderQuad(ModelVertexSink sink, Quad quad, QuadLightData light, ChunkRenderData.Builder renderData, boolean useSodiumLight) {

        final ModelQuadOrientation order = (useSodiumLight || this.useSeparateAo) ? ModelQuadOrientation.orient(light.br) : ModelQuadOrientation.NORMAL;

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
            } else {

                color = (useSodiumLight) ? ColorABGR.mul(quad.getColor(srcIndex), light.br[srcIndex]) : quad.getColor(srcIndex);
            }

            final float u = quad.getTexU(srcIndex);
            final float v = quad.getTexV(srcIndex);

            final int lm = (useSeparateAo) ? ModelQuadUtil.mergeBakedLight(quad.getLight(srcIndex), light.lm[srcIndex]) :
                (useSodiumLight) ? light.lm[srcIndex] : quad.getLight(srcIndex);

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
        public boolean hasNormals;

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
