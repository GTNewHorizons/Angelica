package com.gtnewhorizons.angelica.rendering.celeritas.api;

import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface IrisShaderProvider {

    boolean isShadersEnabled();

    boolean isShadowPass();

    boolean shouldUseFaceCulling();

    @Nullable GlProgram<? extends ChunkShaderInterface> getShaderOverride(TerrainRenderPass pass);

    ChunkVertexType getVertexType(ChunkVertexType defaultType);

    void setRenderPassConfiguration(RenderPassConfiguration<?> configuration);

    /** Block render layer overrides from shader pack */
    @Nullable Map<Block, RenderLayer> getBlockTypeIds();
}
