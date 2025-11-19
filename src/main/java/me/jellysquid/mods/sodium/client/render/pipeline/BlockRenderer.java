package me.jellysquid.mods.sodium.client.render.pipeline;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.ColorMixer;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadView;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadOrientation;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import java.util.List;
import java.util.Random;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.Fluid;

public class BlockRenderer {
    private static final BlockPos POS_ZERO = new BlockPos(0, 0, 0);

    private final Random random = new XoRoShiRoRandom();

    private final QuadLightData cachedQuadLightData = new QuadLightData();

    private final boolean useAmbientOcclusion;
    private final boolean useSodiumAO;
    private boolean useSeparateAo;

    private final LightPipelineProvider lighters;


    public BlockRenderer(LightPipelineProvider lighters) {
        this.lighters = lighters;
        // TODO: Sodium - AO Setting
        this.useAmbientOcclusion = Minecraft.getMinecraft().gameSettings.ambientOcclusion > 0;
        this.useSodiumAO = SodiumClientMod.options().quality.useSodiumAO;
    }

    public boolean renderFluidLogged(IBlockAccess world, Fluid fluid, RenderBlocks renderBlocks, BlockPos pos, ChunkModelBuffers buffers, long seed) {
        if (fluid == null) {
            return false;
        }
        Block block = fluid.getBlock();
        if (block == null) {
            return false;
        }

        boolean rendered = false;
        final int emitted = block.getLightValue(world, pos.x, pos.y, pos.z);

        try {
            final LightMode mode = LightMode.SMOOTH; // TODO: this.getLightingMode(block); is what was previously used. The flat pipeline is busted and was only an optimization for very few blocks.
            final LightPipeline lighter = this.lighters.getLighter(mode);

            TessellatorManager.startCapturing();
            final CapturingTessellator tess = (CapturingTessellator) TessellatorManager.get();
            tess.startDrawingQuads();
            // Use setTranslation rather than setOffset so that the float data written to the internal buffer
            // is done in subchunk-relative coordinates
            tess.setOffset(POS_ZERO);
            tess.setTranslation(-pos.x, -pos.y, -pos.z);
            renderBlocks.renderBlockByRenderType(block, pos.x, pos.y, pos.z);
            final var quads = TessellatorManager.stopCapturingToPooledQuads();
            tess.resetOffset();

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                this.random.setSeed(seed);
                this.renderQuadList(pos, lighter, emitted, buffers, quads, facing, (this.useAmbientOcclusion && this.useSodiumAO));
            }

            if (!quads.isEmpty()) rendered = true;
        } finally {
            TessellatorManager.cleanup();
        }

        return rendered;

    }

    public boolean renderModel(IBlockAccess world, RenderBlocks renderBlocks, Block block, int meta, BlockPos pos, ChunkModelBuffers buffers, boolean cull, long seed) {
        final LightMode mode = LightMode.SMOOTH; // TODO: this.getLightingMode(block); is what was previously used. The flat pipeline is busted and was only an optimization for very few blocks.
        final LightPipeline lighter = this.lighters.getLighter(mode);

        boolean rendered = false;

        this.useSeparateAo = AngelicaConfig.enableIris && BlockRenderingSettings.INSTANCE.shouldUseSeparateAo();
        final int emitted = block.getLightValue(world, pos.x, pos.y, pos.z);

        try {
            TessellatorManager.startCapturing();
            final CapturingTessellator tess = (CapturingTessellator) TessellatorManager.get();
            tess.startDrawingQuads();
            // Use setTranslation rather than setOffset so that the float data written to the internal buffer
            // is done in subchunk-relative coordinates
            tess.setOffset(POS_ZERO);
            tess.setTranslation(-pos.x, -pos.y, -pos.z);
            renderBlocks.renderBlockByRenderType(block, pos.x, pos.y, pos.z);
            final var quads = TessellatorManager.stopCapturingToPooledQuads();
            tess.resetOffset();

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                this.random.setSeed(seed);
                this.renderQuadList(pos, lighter, emitted, buffers, quads, facing, (this.useAmbientOcclusion && this.useSodiumAO));
            }

            if (!quads.isEmpty()) rendered = true;
        } finally {
            TessellatorManager.cleanup();
        }

        return rendered;
    }

    private void renderQuadList(BlockPos pos, LightPipeline lighter, int emitted, ChunkModelBuffers buffers, List<ModelQuadViewMutable> quads, ModelQuadFacing facing, boolean useSodiumLight) {
        final ModelVertexSink sink = buffers.getSink(facing);
        sink.ensureCapacity(quads.size() * 4);

        final ChunkRenderData.Builder renderData = buffers.getRenderData();

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            final var quad = quads.get(i);

            final QuadLightData light = this.cachedQuadLightData;

            if (quad.getNormalFace() != facing)
                continue;

            if (useSodiumLight || this.useSeparateAo)
                lighter.calculate(quad, pos, light, facing, quad.getLightFace(), quad.hasAmbientOcclusion());

            this.renderQuad(sink, quad, emitted, light, renderData, useSodiumLight);
        }

        sink.flush();
    }

    private void renderQuad(ModelVertexSink sink, ModelQuadView quad, int emitted, QuadLightData light, ChunkRenderData.Builder renderData, boolean useSodiumLight) {

        final ModelQuadOrientation order = (useSodiumLight || this.useSeparateAo) ? ModelQuadOrientation.orientByBrightness(light.br, light.lm) : ModelQuadOrientation.NORMAL;

        int shaderBlockId = quad.getShaderBlockId();
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

                color = (useSodiumLight) ? ColorMixer.mulSingleWithoutAlpha(quad.getColor(srcIndex), (int)(light.br[srcIndex] * 255)) : quad.getColor(srcIndex);
            }

            final float u = quad.getTexU(srcIndex);
            final float v = quad.getTexV(srcIndex);

            final int lm = (useSeparateAo) ? ModelQuadUtil.mergeBakedLight(quad.getLight(srcIndex), emitted, light.lm[srcIndex]) :
                (useSodiumLight) ? light.lm[srcIndex] : quad.getLight(srcIndex);

            sink.writeQuad(x, y, z, color, u, v, lm, shaderBlockId);
        }

        final TextureAtlasSprite sprite = (TextureAtlasSprite) quad.celeritas$getSprite();

        if (sprite != null) {
            renderData.addSprite(sprite);
        }
    }

}
