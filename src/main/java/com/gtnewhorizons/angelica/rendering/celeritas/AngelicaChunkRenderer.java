package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.DirectMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.IndirectMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.MultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

class AngelicaChunkRenderer extends DefaultChunkRenderer {
    private GlProgram<? extends ChunkShaderInterface> irisProgram;
    private boolean usingIrisProgram;

    public AngelicaChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
        super(device, renderPassConfiguration, createEmitter());

        final IrisShaderProvider provider = IrisShaderProviderHolder.getProvider();
        if (provider != null) {
            provider.setRenderPassConfiguration(renderPassConfiguration);
        }
    }

    private static MultiDrawEmitter createEmitter() {
        MultiDrawMode mode = AngelicaMod.options().advanced.multiDrawMode;

        if (mode == MultiDrawMode.INDIRECT) {
            boolean supported = GLStateManager.capabilities != null && (GLStateManager.capabilities.OpenGL43 || GLStateManager.capabilities.GL_ARB_multi_draw_indirect);
            if (!supported) {
                AngelicaMod.LOGGER.warn("Indirect multi-draw not supported (requires GL 4.3 / ARB_multi_draw_indirect), falling back to Direct");
                mode = MultiDrawMode.DIRECT;
            }
        }

        return switch (mode) {
            case DIRECT -> new DirectMultiDrawEmitter();
            case INDIRECT -> new IndirectMultiDrawEmitter();
            case INDIVIDUAL -> new IndividualDrawEmitter();
        };
    }

    @Override
    protected void begin(TerrainRenderPass pass) {
        final IrisShaderProvider provider = IrisShaderProviderHolder.getProvider();

        // Check if Iris shaders are active and we have an override
        if (provider != null && provider.isShadersEnabled()) {
            final GlProgram<? extends ChunkShaderInterface> override = provider.getShaderOverride(pass);
            if (override != null) {
                pass.startDrawing();
                override.bind();
                override.getInterface().setupState(pass);
                this.activeProgram = (GlProgram<ChunkShaderInterface>) override;
                this.irisProgram = override;
                this.usingIrisProgram = true;
                return;
            }
        }

        // Fall back to default shader
        this.usingIrisProgram = false;
        this.irisProgram = null;
        super.begin(pass);
    }

    @Override
    protected void end(TerrainRenderPass pass) {
        if (usingIrisProgram && irisProgram != null) {
            irisProgram.getInterface().restoreState();
            irisProgram.unbind();
            irisProgram = null;
            usingIrisProgram = false;
            this.activeProgram = null;
            pass.endDrawing();
            return;
        }

        super.end(pass);
    }

    @Override
    protected boolean useBlockFaceCulling() {
        return IrisShaderProviderHolder.shouldUseFaceCulling();
    }

    @Override
    protected void configureShaderInterface(ChunkShaderInterface shader) {
        if (!usingIrisProgram) {
            shader.setTextureSlot(ChunkShaderTextureSlot.BLOCK, 0);
            shader.setTextureSlot(ChunkShaderTextureSlot.LIGHT, 1);
        }
    }
}
