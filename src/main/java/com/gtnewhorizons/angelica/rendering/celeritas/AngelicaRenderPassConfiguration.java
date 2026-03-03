package com.gtnewhorizons.angelica.rendering.celeritas;

import com.google.common.collect.ImmutableListMultimap;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.opengl.GL11;

import java.util.Map;

public class AngelicaRenderPassConfiguration {

    public static TerrainRenderPass SOLID_PASS, CUTOUT_MIPPED_PASS, TRANSLUCENT_PASS;
    public static Material SOLID_MATERIAL, CUTOUT_MIPPED_MATERIAL, TRANSLUCENT_MATERIAL;

    private record AngelicaPipelineState(int pass, boolean disableAlphaTest) implements TerrainRenderPass.PipelineState {
        @Override
        public void setup() {
            if (pass == 0) {
                GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            }
            if (disableAlphaTest) {
                GLStateManager.disableAlphaTest();
            }
        }

        @Override
        public void clear() {
            if (disableAlphaTest) {
                GLStateManager.enableAlphaTest();
            }
        }
    }

    private static TerrainRenderPass.TerrainRenderPassBuilder builderForRenderType(int pass, boolean disableAlphaTest, ChunkVertexType vertexType) {
        return TerrainRenderPass.builder()
            .pipelineState(new AngelicaPipelineState(pass, disableAlphaTest))
            .vertexType(vertexType)
            .primitiveType(QuadPrimitiveType.TRIANGULATED);
    }

    public static RenderPassConfiguration<BlockRenderLayer> build(ChunkVertexType vertexType) {
        SOLID_PASS = builderForRenderType(0, true, vertexType)
            .name("solid")
            .fragmentDiscard(false)
            .useReverseOrder(false)
            .build();

        CUTOUT_MIPPED_PASS = builderForRenderType(0, false, vertexType)
            .name("cutout_mipped")
            .fragmentDiscard(true)
            .useReverseOrder(false)
            .build();

        TRANSLUCENT_PASS = builderForRenderType(1, false, vertexType)
            .name("translucent")
            .fragmentDiscard(false)
            .useReverseOrder(true)
            .useTranslucencySorting(true)
            .build();

        TRANSLUCENT_MATERIAL = new Material(TRANSLUCENT_PASS, AlphaCutoffParameter.ZERO, true);
        SOLID_MATERIAL = new Material(SOLID_PASS, AlphaCutoffParameter.ZERO, true);
        CUTOUT_MIPPED_MATERIAL = new Material(CUTOUT_MIPPED_PASS, AlphaCutoffParameter.ONE_TENTH, true);

        final ImmutableListMultimap.Builder<BlockRenderLayer, TerrainRenderPass> vanillaRenderStages = ImmutableListMultimap.builder();
        vanillaRenderStages.put(BlockRenderLayer.SOLID, SOLID_PASS);
        vanillaRenderStages.put(BlockRenderLayer.CUTOUT, CUTOUT_MIPPED_PASS);
        vanillaRenderStages.put(BlockRenderLayer.CUTOUT_MIPPED, CUTOUT_MIPPED_PASS);
        vanillaRenderStages.put(BlockRenderLayer.TRANSLUCENT, TRANSLUCENT_PASS);

        // Material lookup by terrain layer
        final Map<BlockRenderLayer, Material> renderTypeToMaterialMap = new Reference2ReferenceOpenHashMap<>(4, Reference2ReferenceOpenHashMap.VERY_FAST_LOAD_FACTOR);
        renderTypeToMaterialMap.put(BlockRenderLayer.SOLID, SOLID_MATERIAL);
        renderTypeToMaterialMap.put(BlockRenderLayer.CUTOUT, CUTOUT_MIPPED_MATERIAL);
        renderTypeToMaterialMap.put(BlockRenderLayer.CUTOUT_MIPPED, CUTOUT_MIPPED_MATERIAL);
        renderTypeToMaterialMap.put(BlockRenderLayer.TRANSLUCENT, TRANSLUCENT_MATERIAL);

        return new RenderPassConfiguration<>(renderTypeToMaterialMap, vanillaRenderStages.build().asMap(), CUTOUT_MIPPED_MATERIAL, CUTOUT_MIPPED_MATERIAL, TRANSLUCENT_MATERIAL);
    }
}
