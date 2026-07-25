package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderParser;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.DirectMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.IndirectMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.MultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderBindingPoints;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.embeddedt.embeddium.impl.render.chunk.shader.DefaultChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

class AngelicaChunkRenderer extends DefaultChunkRenderer {
    private static final int BLOCK_TEXTURE_UNIT = 0;

    private GlProgram<? extends ChunkShaderInterface> irisProgram;
    private boolean usingIrisProgram;
    private int rgssSampler;
    private boolean rgssSamplerResolved;
    private boolean rgssSamplerBound;

    public AngelicaChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
        super(device, renderPassConfiguration, createEmitter());

        final IrisShaderProvider provider = IrisShaderProviderHolder.getProvider();
        if (provider != null) {
            provider.setRenderPassConfiguration(renderPassConfiguration);
        }
    }

    private static MultiDrawEmitter createEmitter() {
        MultiDrawMode mode = ClientProxy.options().advanced.multiDrawMode;

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

    private static GlShader loadShader(ShaderType type, String path, ShaderConstants constants) {
        final String source = ShaderParser.parseShader(ShaderLoader.getShaderSource(path), ShaderLoader::getShaderSource, constants);
        return new GlShader(type, path, source);
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
        bindRgssSampler();
    }

    @Override
    protected void end(TerrainRenderPass pass) {
        unbindRgssSampler();

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

    private void bindRgssSampler() {
        if (!AngelicaRenderPassConfiguration.isRgssEnabled()) {
            return;
        }

        if (!rgssSamplerResolved) {
            rgssSamplerResolved = true;
            rgssSampler = RenderSystem.genSampler();
            if (rgssSampler != 0) {
                RenderSystem.samplerParameteri(rgssSampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                RenderSystem.samplerParameteri(rgssSampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            } else {
                AngelicaMod.LOGGER.warn("Sampler objects unavailable; RGSS terrain filtering will fall back to nearest sampling");
            }
        }

        if (rgssSampler != 0) {
            RenderSystem.bindSamplerToUnit(BLOCK_TEXTURE_UNIT, rgssSampler);
            rgssSamplerBound = true;
        }
    }

    private void unbindRgssSampler() {
        if (rgssSamplerBound) {
            RenderSystem.bindSamplerToUnit(BLOCK_TEXTURE_UNIT, 0);
            rgssSamplerBound = false;
        }
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        unbindRgssSampler();
        RenderSystem.destroySampler(rgssSampler);
        rgssSampler = 0;
        rgssSamplerResolved = false;
    }

    @Override
    protected GlProgram<ChunkShaderInterface> createShader(String path, ChunkShaderOptions options) {
        if (this.enableLegacyGLPatches) {
            return super.createShader(path, options);
        }

        final ShaderConstants constants = options.constants();
        final List<GlShader> loadedShaders = new ArrayList<>();

        try {
            loadedShaders.add(loadShader(ShaderType.VERTEX, "sodium:" + path + ".vsh", constants));
            loadedShaders.add(loadShader(ShaderType.FRAGMENT, "angelica:" + path + ".fsh", constants));

            final var builder = GlProgram.builder("sodium:chunk_shader");
            loadedShaders.forEach(builder::attachShader);
            int i = 0;
            for (var attr : options.pass().vertexType().getVertexFormat().getAttributes()) {
                builder.bindAttribute(attr.getName(), i++);
            }
            builder.bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR);
            return builder.link((shader) -> new DefaultChunkShaderInterface(shader, options));
        } finally {
            loadedShaders.forEach(GlShader::delete);
        }
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
