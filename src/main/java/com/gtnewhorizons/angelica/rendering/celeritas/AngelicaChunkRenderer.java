package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.rendering.culling.GpuTerrainCuller;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import me.jellysquid.mods.sodium.client.gui.options.named.MultiDrawMode;
import com.gtnewhorizons.angelica.rendering.voxelization.SdlShadowVoxelizationSink;
import com.gtnewhorizons.angelica.rendering.voxelization.ShadowVoxelizer;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shadows.ShadowRenderingState;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.IndirectMultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderParser;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderBindingPoints;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.embeddedt.embeddium.impl.render.chunk.shader.DefaultChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;

class AngelicaChunkRenderer extends DefaultChunkRenderer {
    private static final int BLOCK_TEXTURE_UNIT = 0;
    private static final Tracy.ZoneId Z_CHUNK_BEGIN = Tracy.zoneId("chunkBegin", Tracy.COLOR_TERRAIN);
    private static final Tracy.ZoneId Z_CHUNK_ASSEMBLE_REGION = Tracy.zoneId("chunkAssembleRegion", Tracy.COLOR_TERRAIN);

    private GlProgram<? extends ChunkShaderInterface> irisProgram;
    private boolean usingIrisProgram;
    private int rgssSampler;
    private boolean rgssSamplerResolved;
    private boolean rgssSamplerBound;
    private final GpuTerrainCuller culler;
    private final ReusableCachedBatch gpuBatch = new ReusableCachedBatch();

    private static final ShadowVoxelizer shadowVoxelizer = new ShadowVoxelizer();
    private static final SdlShadowVoxelizationSink shadowVoxelSink = new SdlShadowVoxelizationSink();
    private static int loggedVoxelizationSkips;
    private static MultiDrawMode installedBatchMode;

    public AngelicaChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
        super(device, renderPassConfiguration);

        installBatchFactory();
        this.culler = createCuller();

        final IrisShaderProvider provider = IrisShaderProviderHolder.getProvider();
        if (provider != null) {
            provider.setRenderPassConfiguration(renderPassConfiguration);
        }
    }

    private static void installBatchFactory() {
        final MultiDrawMode configured = ClientProxy.options().advanced.multiDrawMode;
        final MultiDrawMode mode = MultiDrawModeResolver.resolve();

        if (BackendManager.RENDER_BACKEND.isIndirectRequired() && mode != MultiDrawMode.INDIRECT) {
            throw new IllegalStateException(
                "Indirect multi-draw was required by the backend but resolved mode is " + mode);
        }

        if (mode == installedBatchMode) return;
        installedBatchMode = mode;

        if (mode != configured) {
            if (mode == MultiDrawMode.INDIRECT) {
                AngelicaMod.LOGGER.info("Backend requires indirect draw; {} -> INDIRECT", configured);
            } else {
                AngelicaMod.LOGGER.warn("Indirect multi-draw not supported (requires GL 4.3 / ARB_multi_draw_indirect), falling back to Direct");
            }
        }

        BatchAssembler.setBatchFactory(switch (mode) {
            case DIRECT -> DirectMultiDrawBatch::new;
            case INDIRECT -> IndirectMultiDrawBatch::new;
            case INDIVIDUAL -> IndividualDrawBatch::new;
        });
    }

    private enum VoxelizationSkip {
        NO_RENDER_LISTS("pass has no render lists"),
        NO_DEFERRED_PIPELINE("no deferred pipeline"),
        NO_VOXELIZATION_COMPUTE("pack declares no shadow voxelization");

        private final String message;

        VoxelizationSkip(String message) {
            this.message = message;
        }
    }

    private static void logVoxelizationSkipOnce(VoxelizationSkip reason) {
        final int bit = 1 << reason.ordinal();
        if ((loggedVoxelizationSkips & bit) != 0) return;
        loggedVoxelizationSkips |= bit;
        AngelicaMod.LOGGER.info("shadow voxelization skipped: {}", reason.message);
    }

    private static GpuTerrainCuller createCuller() {
        final GpuCulling.Availability availability = GpuCulling.availability();
        if (availability == GpuCulling.Availability.AVAILABLE) {
            AngelicaMod.LOGGER.info("Compute-driven chunk culling available, mode={}", GpuCulling.mode());
            return new GpuTerrainCuller(GpuCulling.culler(), GpuCulling.sectionMeta());
        }
        AngelicaMod.LOGGER.warn("GPU culling unavailable ({}); terrain will use CPU culling", availability);
        return null;
    }

    private static GlShader loadShader(ShaderType type, String path, ShaderConstants constants) {
        final String source = ShaderParser.parseShader(ShaderLoader.getShaderSource(path), ShaderLoader::getShaderSource, constants);
        return new GlShader(type, path, source);
    }

    @Override
    protected void begin(TerrainRenderPass pass) {
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

        if (culler != null) {
            culler.delete();
        }

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
    public void render(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass, CameraTransform occlusionCamera, CameraTransform camera) {
        if (culler != null) {
            culler.beginRenderPass(matrices, renderLists, renderPass, occlusionCamera, camera, useBlockFaceCulling());
        }

        try {
            voxelizeShadowTerrain(matrices, renderLists, renderPass, occlusionCamera, camera);
            super.render(matrices, commandList, renderLists, renderPass, occlusionCamera, camera);
        } finally {
            if (culler != null) {
                culler.endPass();
            }
        }
    }

    @Override
    protected @Nullable CachedBatch getRegionBatch(CommandList commandList, RenderRegion region,
                                                   SectionRenderDataStorage storage, ChunkRenderList renderList,
                                                   CameraTransform occlusionCamera, TerrainRenderPass renderPass,
                                                   boolean useBlockFaceCulling,
                                                   SectionRenderDataStorage.BatchCacheParams cacheParams) {
        if (culler != null && culler.isComputeActiveThisPass()) {
            final MultiDrawBatch batch = culler.batchForRegion(region);
            if (batch == null) return null;
            return this.gpuBatch.wrap(batch, prepareTessellation(commandList, region, renderPass));
        }

        if (!Tracy.ENABLED) {
            return super.getRegionBatch(commandList, region, storage, renderList, occlusionCamera, renderPass,
                useBlockFaceCulling, cacheParams);
        }

        final long batchesCreatedBefore = BatchAssembler.getCachedBatchesCreated();
        Tracy.beginZone(Z_CHUNK_ASSEMBLE_REGION);
        try {
            final CachedBatch cached = super.getRegionBatch(commandList, region, storage, renderList, occlusionCamera,
                renderPass, useBlockFaceCulling, cacheParams);
            if (BatchAssembler.getCachedBatchesCreated() != batchesCreatedBefore) TerrainDrawStats.recordRebuild();
            final MultiDrawBatch batch = cached != null ? cached.getBatch() : null;
            if (batch != null && !batch.isEmpty()) TerrainDrawStats.recordBatch(batch.size());
            return cached;
        } finally {
            Tracy.endZone();
        }
    }

    @Override
    protected void executeBatch(CommandList commandList, MultiDrawBatch batch, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        TerrainDrawStats.beginExecuteZone();
        try {
            super.executeBatch(commandList, batch, tessellation, primitiveType);
        } finally {
            TerrainDrawStats.endExecuteZone();
        }
    }

    private static final class ReusableCachedBatch extends CachedBatch {
        private static final byte[] NO_SECTIONS = new byte[0];

        private MultiDrawBatch batch;
        private GlTessellation tessellation;

        ReusableCachedBatch() {
            super(null, null, NO_SECTIONS, 0, 0, 0, 0, 0, 0, 0);
        }

        CachedBatch wrap(MultiDrawBatch batch, GlTessellation tessellation) {
            this.batch = batch;
            this.tessellation = tessellation;
            return this;
        }

        @Override
        public MultiDrawBatch getBatch() {
            return this.batch;
        }

        @Override
        public GlTessellation getTessellation() {
            return this.tessellation;
        }
    }

    private void voxelizeShadowTerrain(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform occlusionCamera, CameraTransform camera) {
        if (!BackendManager.RENDER_BACKEND.isSDLGPU()) return;
        if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()) return;
        if (!renderLists.hasPass(renderPass)) {
            logVoxelizationSkipOnce(VoxelizationSkip.NO_RENDER_LISTS);
            return;
        }

        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(pipeline instanceof DeferredWorldRenderingPipeline deferred)) {
            logVoxelizationSkipOnce(VoxelizationSkip.NO_DEFERRED_PIPELINE);
            return;
        }
        final ComputeProgram voxelCompute = deferred.getShadowVoxelizationCompute();
        if (voxelCompute == null) {
            logVoxelizationSkipOnce(VoxelizationSkip.NO_VOXELIZATION_COMPUTE);
            return;
        }

        final int prevProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        voxelCompute.use();
        deferred.prepareShadowVoxelizationCompute(matrices.modelView());
        try {
            shadowVoxelizer.walkPass(renderLists, renderPass, renderPass.vertexType().getVertexFormat(), camera, occlusionCamera, useBlockFaceCulling(), shadowVoxelSink);
        } finally {
            if (prevProgram != 0) GLStateManager.glUseProgram(prevProgram);
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
