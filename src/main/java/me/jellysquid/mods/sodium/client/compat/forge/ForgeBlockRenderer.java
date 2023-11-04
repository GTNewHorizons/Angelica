package me.jellysquid.mods.sodium.client.compat.forge;

import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;
import net.minecraftforge.client.model.pipeline.VertexLighterSmoothAo;
import net.minecraftforge.common.ForgeConfig;

import java.util.List;
import java.util.Random;

/**
 * Utility class for BlockRenderer, that uses the Forge lighting pipeline.
 *
 * This class is derived from Forge's renderer, and is licensed under LGPL-2.1. As the class is a standalone class file,
 * it may be replaced in an existing copy of Embeddium with an alternate LGPL-2.1 implementation.
 */
public class ForgeBlockRenderer {
    private final BlockColors colors = MinecraftClient.getInstance().getBlockColors();
    private final ThreadLocal<VertexLighterFlat> lighterFlat = ThreadLocal.withInitial(() -> new VertexLighterFlat(colors));
    private final ThreadLocal<VertexLighterSmoothAo> lighterSmooth = ThreadLocal.withInitial(() -> new VertexLighterSmoothAo(colors));
    private final ThreadLocal<VertexBufferConsumer> consumerFlat = ThreadLocal.withInitial(VertexBufferConsumer::new);
    private final ThreadLocal<VertexBufferConsumer> consumerSmooth = ThreadLocal.withInitial(VertexBufferConsumer::new);


    private static boolean useForgeLightingPipeline = false;

    public static void init() {
        useForgeLightingPipeline = ForgeConfig.CLIENT.experimentalForgeLightPipelineEnabled.get();
    }

    public static boolean useForgeLightingPipeline() {
        return useForgeLightingPipeline;
    }

    private void processQuad(ChunkRenderData.Builder renderData, BakedQuad quad) {
        ModelQuadView src = (ModelQuadView)quad;
        Sprite sprite = src.rubidium$getSprite();

        if (sprite != null) {
            renderData.addSprite(sprite);
        }
    }

    public boolean renderBlock(LightMode mode, BlockState state, BlockPos pos, BlockRenderView world, BakedModel model, MatrixStack stack,
                               VertexConsumer buffer, Random random, long seed, IModelData data, boolean checkSides, BlockOcclusionCache sideCache,
                               ChunkRenderData.Builder renderData) {
        VertexBufferConsumer consumer = mode == LightMode.FLAT ? this.consumerFlat.get() : this.consumerSmooth.get();
        consumer.setBuffer(buffer);
        VertexLighterFlat lighter = mode == LightMode.FLAT ? this.lighterFlat.get() : this.lighterSmooth.get();
        lighter.setParent(consumer);
        lighter.setTransform(stack.peek());

        // render
        lighter.setWorld(world);
        lighter.setState(state);
        lighter.setBlockPos(pos);
        boolean empty = true;
        random.setSeed(seed);

        List<BakedQuad> quads = model.getQuads(state, null, random, data);
        if(!quads.isEmpty()) {
            lighter.updateBlockInfo();
            empty = false;
            // noinspection ForLoopReplaceableByForEach
            for(int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                quad.pipe(lighter);
                processQuad(renderData, quad);
            }
        }

        for(Direction side : DirectionUtil.ALL_DIRECTIONS)
        {
            random.setSeed(seed);
            quads = model.getQuads(state, side, random, data);
            if(!quads.isEmpty())
            {
                if(!checkSides || sideCache.shouldDrawSide(state, world, pos, side))
                {
                    if(empty) lighter.updateBlockInfo();
                    empty = false;
                    // noinspection ForLoopReplaceableByForEach
                    for(int i = 0; i < quads.size(); i++) {
                        BakedQuad quad = quads.get(i);
                        quad.pipe(lighter);
                        processQuad(renderData, quad);
                    }
                }
            }
        }
        lighter.resetBlockInfo();
        return !empty;
    }
}
