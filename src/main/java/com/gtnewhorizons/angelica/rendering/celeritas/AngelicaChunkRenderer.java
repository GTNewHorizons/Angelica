package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.rendering.culling.GpuIndirectMultiDrawEmitter;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderParser;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.DrawCommandSink;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
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
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

class AngelicaChunkRenderer extends DefaultChunkRenderer {
    private static final int BLOCK_TEXTURE_UNIT = 0;
    private static final Tracy.ZoneId Z_CHUNK_BEGIN = Tracy.zoneId("chunkBegin", Tracy.COLOR_TERRAIN);
    private static final Tracy.ZoneId Z_CHUNK_REGION_FIRST = Tracy.zoneId("chunkRegionFirst", Tracy.COLOR_TERRAIN);
    private static final Tracy.ZoneId Z_CHUNK_REGION = Tracy.zoneId("chunkRegion", Tracy.COLOR_TERRAIN);
    private static final Tracy.ZoneId Z_CHUNK_ASSEMBLE_REGION = Tracy.zoneId("chunkAssembleRegion", Tracy.COLOR_TERRAIN);
    private static final Tracy.ZoneId Z_CHUNK_EXECUTE_BATCH = Tracy.zoneId("chunkExecuteBatch", Tracy.COLOR_TERRAIN);

    private GlProgram<? extends ChunkShaderInterface> irisProgram;
    private boolean usingIrisProgram;
    private int rgssSampler;
    private boolean rgssSamplerResolved;
    private boolean rgssSamplerBound;
    private int regionIndex;

    public AngelicaChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
        super(device, renderPassConfiguration, createEmitter());

        final IrisShaderProvider provider = IrisShaderProviderHolder.getProvider();
        if (provider != null) {
            provider.setRenderPassConfiguration(renderPassConfiguration);
        }
    }

    private static MultiDrawEmitter createEmitter() {
        final MultiDrawMode configured = ClientProxy.options().advanced.multiDrawMode;
        final MultiDrawMode mode = MultiDrawModeResolver.resolve();

        if (mode != configured) {
            if (mode == MultiDrawMode.INDIRECT) {
                AngelicaMod.LOGGER.info("Backend requires indirect draw; {} -> INDIRECT", configured);
            } else {
                AngelicaMod.LOGGER.warn("Indirect multi-draw not supported (requires GL 4.3 / ARB_multi_draw_indirect), falling back to Direct");
            }
        }

        if (BackendManager.RENDER_BACKEND.isIndirectRequired() && mode != MultiDrawMode.INDIRECT) {
            throw new IllegalStateException("Indirect multi-draw was required by the backend but resolved mode is " + mode);
        }

        final GpuCulling.Availability availability = GpuCulling.availability();
        if (availability == GpuCulling.Availability.AVAILABLE) {
            AngelicaMod.LOGGER.info("Compute-driven chunk culling available, mode={}", GpuCulling.mode());
            return new GpuIndirectMultiDrawEmitter(GpuCulling.culler(), GpuCulling.sectionMeta());
        }
        AngelicaMod.LOGGER.warn("GPU culling unavailable ({}); terrain will use CPU culling", availability);

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
        this.regionIndex = 0;
        if (Tracy.ENABLED) Tracy.beginZone(Z_CHUNK_BEGIN);
        try {
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
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
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
    protected void drawRegion(ChunkShaderInterface shader, CommandList commandList, RenderRegion region,
                              CameraTransform camera, long timestamp) {
        if (Tracy.ENABLED) Tracy.beginZone(regionIndex == 0 ? Z_CHUNK_REGION_FIRST : Z_CHUNK_REGION);
        try {
            regionIndex++;
            super.drawRegion(shader, commandList, region, camera, timestamp);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    @Override
    protected void assembleRegion(DrawCommandSink sink, RenderRegion region, SectionRenderDataStorage storage,
                                  ChunkRenderList renderList, CameraTransform occlusionCamera,
                                  TerrainRenderPass renderPass, boolean useBlockFaceCulling) {
        if (getEmitter() instanceof GpuIndirectMultiDrawEmitter gpu && gpu.isComputeActiveThisPass()) {
            gpu.prepareRegion(region);
            return;
        }

        if (Tracy.ENABLED) Tracy.beginZone(Z_CHUNK_ASSEMBLE_REGION);
        try {
            super.assembleRegion(sink, region, storage, renderList, occlusionCamera, renderPass, useBlockFaceCulling);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    @Override
    protected void executeBatch(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        if (Tracy.ENABLED) {
            final MultiDrawEmitter emitter = getEmitter();
            // The compute path reports itself through GpuIndirectMultiDrawEmitter; counting it here too double-counts.
            if (!(emitter instanceof GpuIndirectMultiDrawEmitter gpu && gpu.isComputeActiveThisPass())) {
                TerrainDrawStats.recordBatch(emitter.getPendingCommandCount());
            }
            Tracy.beginZone(Z_CHUNK_EXECUTE_BATCH);
        }
        try {
            super.executeBatch(commandList, tessellation, primitiveType);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
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
