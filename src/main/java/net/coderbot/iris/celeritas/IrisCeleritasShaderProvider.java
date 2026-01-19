package net.coderbot.iris.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class IrisCeleritasShaderProvider implements IrisShaderProvider {
    private static final IrisExtendedChunkVertexType EXTENDED_VERTEX_TYPE = new IrisExtendedChunkVertexType();

    private final IrisCeleritasChunkProgramOverrides overrides = new IrisCeleritasChunkProgramOverrides();
    private RenderPassConfiguration<?> renderPassConfiguration;

    @Override
    public boolean isShadersEnabled() {
        return Iris.getCurrentPack().isPresent();
    }

    @Override
    public boolean isShadowPass() {
        return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
    }

    @Override
    public boolean shouldUseFaceCulling() {
        return !ShadowRenderingState.areShadowsCurrentlyBeingRendered();
    }

    @Override
    @Nullable
    public GlProgram<? extends ChunkShaderInterface> getShaderOverride(TerrainRenderPass pass) {
        if (!isShadersEnabled() || renderPassConfiguration == null) {
            return null;
        }
        return overrides.getProgramOverride(pass, renderPassConfiguration);
    }

    @Override
    public ChunkVertexType getVertexType(ChunkVertexType defaultType) {
        if (isShadersEnabled() && BlockRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()) {
            return EXTENDED_VERTEX_TYPE;
        }
        return defaultType;
    }

    @Override
    public void setRenderPassConfiguration(RenderPassConfiguration<?> configuration) {
        this.renderPassConfiguration = configuration;
    }

    @Override
    @Nullable
    public Map<Block, RenderLayer> getBlockTypeIds() {
        return BlockRenderingSettings.INSTANCE.getBlockTypeIds();
    }

    @Override
    @Nullable
    public Object2IntMap<Block> getBlockMatches() {
        return null;
    }

    public void deleteShaders() {
        overrides.deleteShaders();
    }

    public IrisCeleritasChunkProgramOverrides getOverrides() {
        return overrides;
    }
}
