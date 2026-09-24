package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FBOClearTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.opengl.GL20;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUColorTargetDescription;
import org.lwjgl.sdl.SDL_GPUDepthStencilState;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineCreateInfo;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineTargetInfo;
import org.lwjgl.sdl.SDL_GPUMultisampleState;
import org.lwjgl.sdl.SDL_GPURasterizerState;
import org.lwjgl.sdl.SDL_GPUVertexInputState;
import org.lwjgl.sdl.SDL_GPUViewport;
import org.lwjgl.sdl.SDL_Rect;

import java.util.Arrays;
import java.util.function.LongSupplier;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class AttachmentClear {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final String VERTEX_SOURCE = "angelica:sdlgpu/clear_attachment.vsh";
    private static final String FRAGMENT_SOURCE = "angelica:sdlgpu/clear_attachment.fsh";
    private static final long KEY_SEED = 0x5B1D2A7C4E9F3061L;
    private static final int CACHE_SLOTS = 4;

    private final FrameManager frameManager;
    private final PipelineStore pipelineStore;
    private final ShaderManager shaderManager;

    private int program;
    private long sdlVertexShader;
    private long sdlFragmentShader;
    private boolean failed;

    private final long[] cacheKeys = new long[CACHE_SLOTS];
    private final long[] cachePipelines = new long[CACHE_SLOTS];
    private int cacheNext;

    private final int[] scissorRect = new int[4];

    private final Builder builder = new Builder();
    private int[] buildColorFormats;
    private int buildColorCount;
    private int buildDepthFormat;
    private boolean buildDepthFlag;
    private boolean buildStencilFlag;
    private int buildWriteMask;

    public AttachmentClear(FrameManager frameManager, PipelineStore pipelineStore, ShaderManager shaderManager) {
        this.frameManager = frameManager;
        this.pipelineStore = pipelineStore;
        this.shaderManager = shaderManager;
    }

    private final class Builder implements LongSupplier {
        @Override public long getAsLong() { return createPipeline(); }
    }

    public boolean eligible(ContextState st, FrameState f, FboState fbo, boolean wantDepth, boolean wantStencil) {
        if (SystemProperties.SDL_DISABLE_IN_PASS_CLEAR) return false;
        if (st.boundFboId == 0) return false;
        if (f.renderPass == 0) return false;
        if (fbo.depthTexture == 0) return false;
        if (f.currentDepthTarget != fbo.depthTexture) return false;
        if (f.activeLayoutHash != fbo.structuralLayoutHash || f.currentColorTarget != fbo.primaryTarget) return false;
        if (FBOClearTracker.fboHasPendingClear(st, fbo)) return false;
        if (wantStencil && !PixelOps.isDepthStencilFormat(fbo.depthFormat)) return false;
        if (fbo.width <= 0 || fbo.height <= 0) return false;
        return computeScissor(st, fbo);
    }

    private boolean computeScissor(ContextState st, FboState fbo) {
        final int sx, sy, sw, sh;
        if (st.scissorEnabled) {
            sx = st.scissorX;
            sy = st.scissorY;
            sw = st.scissorW;
            sh = st.scissorH;
        } else {
            sx = 0;
            sy = 0;
            sw = fbo.width;
            sh = fbo.height;
        }
        return ScissorClamp.clamp(sx, sy, sw, sh, fbo.width, fbo.height, scissorRect);
    }

    public boolean clearInPass(ContextState st, FrameState f, FboState fbo, boolean depth, boolean stencil) {
        final int writeMask = stencil ? (st.pipeline.stencilWriteMask & 0xFF) : 0;
        final long pipeline = getOrCreatePipeline(fbo, depth, stencil, writeMask);
        if (pipeline == 0) return false;
        if (!computeScissor(st, fbo)) return false;

        final long rp = f.renderPass;
        SDL_BindGPUGraphicsPipeline(rp, pipeline);

        final float clearDepth = Math.min(1.0f, Math.max(0.0f, st.depthClearValue));
        final long vpAddr = st.cachedViewport.address();
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.X, 0f);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.Y, 0f);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.W, fbo.width);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.H, fbo.height);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.MIN_DEPTH, clearDepth);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.MAX_DEPTH, clearDepth);
        SDL_SetGPUViewport(rp, st.cachedViewport);

        final long scAddr = st.cachedScissor.address();
        MemoryAccess.putInt(scAddr + SDL_Rect.X, scissorRect[ScissorClamp.X]);
        MemoryAccess.putInt(scAddr + SDL_Rect.Y, scissorRect[ScissorClamp.Y]);
        MemoryAccess.putInt(scAddr + SDL_Rect.W, scissorRect[ScissorClamp.W]);
        MemoryAccess.putInt(scAddr + SDL_Rect.H, scissorRect[ScissorClamp.H]);
        SDL_SetGPUScissor(rp, st.cachedScissor);

        if (stencil) {
            SDL_SetGPUStencilReference(rp, (byte) st.stencilClearValue);
        }

        SDL_DrawGPUPrimitives(rp, 3, 1, 0, 0);

        st.lastBoundPipeline = 0;
        st.viewportDirty = true;
        st.scissorDirty = true;
        st.lastAppliedStencilRef = Integer.MIN_VALUE;
        st.lastAppliedVboBindCb = 0;

        if (depth) st.clearedTexturesThisFrame.add(fbo.depthTexture);
        if (stencil) st.clearedStencilTexturesThisFrame.add(fbo.depthTexture);

        frameManager.noteInPassClear();
        return true;
    }

    long getOrCreatePipeline(FboState fbo, boolean depthFlag, boolean stencilFlag, int writeMask) {
        if (!ensureShaders()) return 0;

        final int[] formats = fbo.cachedColorFormats;
        final int count = formats != null ? formats.length : 0;
        long key = KEY_SEED;
        key = Hashing.fmix64(key, count);
        for (int i = 0; i < count; i++) key = Hashing.fmix64(key, formats[i]);
        key = Hashing.fmix64(key, fbo.depthFormat);
        key = Hashing.fmix64(key, (depthFlag ? 1 : 0) | ((stencilFlag ? 1 : 0) << 1) | (writeMask << 8));

        for (int i = 0; i < CACHE_SLOTS; i++) {
            if (cacheKeys[i] == key && cachePipelines[i] != 0) return cachePipelines[i];
        }

        buildColorFormats = formats;
        buildColorCount = count;
        buildDepthFormat = fbo.depthFormat;
        buildDepthFlag = depthFlag;
        buildStencilFlag = stencilFlag;
        buildWriteMask = writeMask;
        final long pipeline = pipelineStore.getOrBuild(key, builder);
        if (pipeline != 0) {
            cacheKeys[cacheNext] = key;
            cachePipelines[cacheNext] = pipeline;
            cacheNext = (cacheNext + 1) & (CACHE_SLOTS - 1);
        }
        return pipeline;
    }

    private long createPipeline() {
        try (var stack = stackPush()) {
            final int numColorTargets = buildColorCount;
            final SDL_GPUColorTargetDescription.Buffer colorDesc = SDL_GPUColorTargetDescription.calloc(numColorTargets, stack);
            for (int i = 0; i < numColorTargets; i++) {
                final var desc = colorDesc.get(i);
                desc.format(buildColorFormats[i]);
                desc.blend_state()
                    .enable_blend(false)
                    .color_write_mask((byte) 0)
                    .enable_color_write_mask(true);
            }

            final SDL_GPUDepthStencilState depthStencil = SDL_GPUDepthStencilState.calloc(stack)
                .enable_depth_test(buildDepthFlag)
                .enable_depth_write(buildDepthFlag)
                .compare_op(SDL_GPU_COMPAREOP_ALWAYS)
                .enable_stencil_test(buildStencilFlag)
                .compare_mask((byte) 0xFF)
                .write_mask((byte) buildWriteMask);
            if (buildStencilFlag) {
                depthStencil.front_stencil_state()
                    .fail_op(SDL_GPU_STENCILOP_REPLACE)
                    .pass_op(SDL_GPU_STENCILOP_REPLACE)
                    .depth_fail_op(SDL_GPU_STENCILOP_REPLACE)
                    .compare_op(SDL_GPU_COMPAREOP_ALWAYS);
                depthStencil.back_stencil_state()
                    .fail_op(SDL_GPU_STENCILOP_REPLACE)
                    .pass_op(SDL_GPU_STENCILOP_REPLACE)
                    .depth_fail_op(SDL_GPU_STENCILOP_REPLACE)
                    .compare_op(SDL_GPU_COMPAREOP_ALWAYS);
            }

            final SDL_GPURasterizerState rasterizer = SDL_GPURasterizerState.calloc(stack)
                .cull_mode(SDL_GPU_CULLMODE_NONE)
                .front_face(SDL_GPU_FRONTFACE_COUNTER_CLOCKWISE)
                .fill_mode(SDL_GPU_FILLMODE_FILL);

            final SDL_GPUMultisampleState multisample = SDL_GPUMultisampleState.calloc(stack)
                .sample_count(SDL_GPU_SAMPLECOUNT_1);

            final SDL_GPUGraphicsPipelineTargetInfo targetInfo = SDL_GPUGraphicsPipelineTargetInfo.calloc(stack)
                .num_color_targets(numColorTargets)
                .color_target_descriptions(colorDesc)
                .depth_stencil_format(buildDepthFormat)
                .has_depth_stencil_target(true);

            final SDL_GPUVertexInputState vertexInput = SDL_GPUVertexInputState.calloc(stack);

            final SDL_GPUGraphicsPipelineCreateInfo ci = SDL_GPUGraphicsPipelineCreateInfo.calloc(stack)
                .vertex_shader(sdlVertexShader)
                .fragment_shader(sdlFragmentShader)
                .primitive_type(SDL_GPU_PRIMITIVETYPE_TRIANGLELIST)
                .rasterizer_state(rasterizer)
                .multisample_state(multisample)
                .depth_stencil_state(depthStencil)
                .target_info(targetInfo)
                .vertex_input_state(vertexInput);

            final long pipeline = SDL_CreateGPUGraphicsPipeline(pipelineStore.device().getDevice(), ci);
            if (pipeline == 0) {
                LOG.error("AttachmentClear: pipeline creation failed: {}", SDLError.SDL_GetError());
                return PipelineStore.BAD_PIPELINE_SENTINEL;
            }
            return pipeline;
        }
    }

    private boolean ensureShaders() {
        if (program != 0) return true;
        if (failed) return false;
        failed = true;

        final int vs = shaderManager.createShader(GL20.GL_VERTEX_SHADER);
        shaderManager.shaderSource(vs, ShaderLoader.getShaderSource(VERTEX_SOURCE));
        shaderManager.compileShader(vs);
        final int fs = shaderManager.createShader(GL20.GL_FRAGMENT_SHADER);
        shaderManager.shaderSource(fs, ShaderLoader.getShaderSource(FRAGMENT_SOURCE));
        shaderManager.compileShader(fs);

        final int prog = shaderManager.createProgram();
        shaderManager.attachShader(prog, vs);
        shaderManager.attachShader(prog, fs);
        shaderManager.linkProgram(prog);

        final ShaderManager.ProgramObject po = shaderManager.getProgram(prog);
        if (po == null || !po.linked) {
            LOG.error("AttachmentClear: clear_attachment program failed to link: {}", po == null ? "no program object" : po.infoLog);
            return false;
        }

        po.sdlVertexShader = shaderManager.createSDLShader(po.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, po.vertexResources.numSamplers(), po.vertexResources.numUBOs(), po.vertexResources.numStorageBuffers(), po.vertexResources.numStorageTextures());
        po.sdlFragmentShader = shaderManager.createSDLShader(po.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, po.fragmentResources.numSamplers(), po.fragmentResources.numUBOs(), po.fragmentResources.numStorageBuffers(), po.fragmentResources.numStorageTextures());
        if (po.sdlVertexShader == 0 || po.sdlFragmentShader == 0) {
            LOG.error("AttachmentClear: clear_attachment SDL shader creation failed");
            return false;
        }

        program = prog;
        sdlVertexShader = po.sdlVertexShader;
        sdlFragmentShader = po.sdlFragmentShader;
        failed = false;
        return true;
    }

    public void shutdown() {
        program = 0;
        sdlVertexShader = 0;
        sdlFragmentShader = 0;
        failed = false;
        Arrays.fill(cacheKeys, 0L);
        Arrays.fill(cachePipelines, 0L);
        cacheNext = 0;
    }
}
