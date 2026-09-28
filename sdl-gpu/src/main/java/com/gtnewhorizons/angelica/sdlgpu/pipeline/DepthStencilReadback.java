package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FBOClearTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_FColor;
import org.lwjgl.sdl.SDL_GPUColorTargetDescription;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilState;
import org.lwjgl.sdl.SDL_GPUDepthStencilTargetInfo;
import org.lwjgl.sdl.SDL_GPUSamplerCreateInfo;
import org.lwjgl.sdl.SDL_GPUTextureSamplerBinding;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.LongSupplier;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class DepthStencilReadback {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final String DEPTH_FRAGMENT_SOURCE = "angelica:sdlgpu/readback_depth.fsh";
    private static final String STENCIL_FRAGMENT_SOURCE = "angelica:sdlgpu/readback_stencil.fsh";
    private static final long KEY_SEED = 0x3C6EF372A54FF53AL;
    private static final int STENCIL_BITS = 8;
    private static final int DEPTH_TARGET_FORMAT = SDL_GPU_TEXTUREFORMAT_R32_FLOAT;
    private static final int STENCIL_TARGET_FORMAT = SDL_GPU_TEXTUREFORMAT_R8_UNORM;

    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final PipelineStore pipelineStore;
    private final FBOClearTracker fboClearTracker;
    private final FullscreenPass fullscreen;

    private long depthFragmentShader;
    private boolean depthFragmentFailed;
    private long stencilFragmentShader;
    private boolean stencilFragmentFailed;
    private long nearestSampler;

    private long depthPipeline;
    private final long[] stencilPipelines = new long[STENCIL_BITS];
    private int stencilPipelinesFormat;

    private final Builder builder = new Builder();
    private int buildBit;
    private int buildDepthFormat;

    public DepthStencilReadback(FrameManager frameManager, ResourceManager resourceManager, PipelineStore pipelineStore, ShaderManager shaderManager, FBOClearTracker fboClearTracker) {
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.pipelineStore = pipelineStore;
        this.fboClearTracker = fboClearTracker;
        this.fullscreen = new FullscreenPass(shaderManager);
    }

    private final class Builder implements LongSupplier {
        @Override public long getAsLong() { return buildBit < 0 ? createDepthPipeline() : createStencilPipeline(buildBit, buildDepthFormat); }
    }

    public long renderDepth(ContextState st, long depthTexture, int depthSdlFormat, int width, int height) {
        final FrameState f = frameManager.frame();
        if (!canRender(f, depthTexture, width, height)) return 0L;
        if (depthTexture == resourceManager.getSwapchainDepthStencil() && !resourceManager.isSwapchainDepthStencilSampleable()) return 0L;
        final long pipeline = getDepthPipeline();
        final long sampler = getNearestSampler();
        if (pipeline == 0L || sampler == 0L) return 0L;
        applyPendingClears(st, f, depthTexture);
        final long scratch = ensureDepthScratch(st, width, height);

        frameManager.endRenderPassIfActive(f, FrameManager.PASS_END_READBACK);
        try (MemoryStack stack = stackPush()) {
            final SDL_GPUColorTargetInfo.Buffer color = colorTarget(stack, scratch, SDL_GPU_LOADOP_DONT_CARE);
            final long rp = frameManager.beginRenderPass(color, null);
            SDL_BindGPUGraphicsPipeline(rp, pipeline);
            FullscreenPass.setViewport(rp, st, width, height, 0f);
            FullscreenPass.setScissor(rp, st, 0, 0, width, height);
            final SDL_GPUTextureSamplerBinding.Buffer binding = SDL_GPUTextureSamplerBinding.calloc(1, stack);
            binding.get(0).texture(depthTexture).sampler(sampler);
            SDL_BindGPUFragmentSamplers(rp, 0, binding);
            SDL_DrawGPUPrimitives(rp, 3, 1, 0, 0);
            frameManager.endRenderPassIfActive(f, FrameManager.PASS_END_READBACK);
        }
        FullscreenPass.markStateClobbered(st);
        return scratch;
    }

    public long renderStencil(ContextState st, long depthStencilTexture, int depthSdlFormat, int width, int height) {
        final FrameState f = frameManager.frame();
        if (!canRender(f, depthStencilTexture, width, height)) return 0L;
        if (!ensureStencilPipelines(depthSdlFormat)) return 0L;
        applyPendingClears(st, f, depthStencilTexture);
        final long scratch = ensureStencilScratch(st, width, height);

        frameManager.endRenderPassIfActive(f, FrameManager.PASS_END_READBACK);
        try (MemoryStack stack = stackPush()) {
            final SDL_GPUColorTargetInfo.Buffer color = colorTarget(stack, scratch, SDL_GPU_LOADOP_CLEAR);
            final SDL_GPUDepthStencilTargetInfo ds = SDL_GPUDepthStencilTargetInfo.calloc(stack);
            final long dsAddr = ds.address();
            MemoryAccess.putAddress(dsAddr + SDL_GPUDepthStencilTargetInfo.TEXTURE, depthStencilTexture);
            MemoryAccess.putInt(dsAddr + SDL_GPUDepthStencilTargetInfo.LOAD_OP, SDL_GPU_LOADOP_LOAD);
            MemoryAccess.putInt(dsAddr + SDL_GPUDepthStencilTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
            MemoryAccess.putInt(dsAddr + SDL_GPUDepthStencilTargetInfo.STENCIL_LOAD_OP, SDL_GPU_LOADOP_LOAD);
            MemoryAccess.putInt(dsAddr + SDL_GPUDepthStencilTargetInfo.STENCIL_STORE_OP, SDL_GPU_STOREOP_STORE);

            final long rp = frameManager.beginRenderPass(color, ds);
            final long cb = frameManager.getCommandBuffer(f);
            FullscreenPass.setViewport(rp, st, width, height, 0f);
            FullscreenPass.setScissor(rp, st, 0, 0, width, height);
            final ByteBuffer bitValue = stack.calloc(16);
            for (int b = 0; b < STENCIL_BITS; b++) {
                SDL_BindGPUGraphicsPipeline(rp, stencilPipelines[b]);
                SDL_SetGPUStencilReference(rp, (byte) (1 << b));
                bitValue.putFloat(0, (1 << b) / 255.0f);
                SDL_PushGPUFragmentUniformData(cb, 0, bitValue);
                SDL_DrawGPUPrimitives(rp, 3, 1, 0, 0);
            }
            frameManager.endRenderPassIfActive(f, FrameManager.PASS_END_READBACK);
        }
        FullscreenPass.markStateClobbered(st);
        st.lastPushedProgramFs = -1;
        return scratch;
    }

    private static boolean canRender(FrameState f, long texture, int width, int height) {
        return f.frameActive && f.commandBuffer != 0L && texture != 0L && width > 0 && height > 0;
    }

    private void applyPendingClears(ContextState st, FrameState f, long texture) {
        fboClearTracker.materializePendingClearForRead(st, f, texture);
    }

    private static SDL_GPUColorTargetInfo.Buffer colorTarget(MemoryStack stack, long texture, int loadOp) {
        final SDL_GPUColorTargetInfo.Buffer color = SDL_GPUColorTargetInfo.calloc(1, stack);
        final long addr = color.get(0).address();
        MemoryAccess.putAddress(addr + SDL_GPUColorTargetInfo.TEXTURE, texture);
        MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.LOAD_OP, loadOp);
        MemoryAccess.putInt(addr + SDL_GPUColorTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
        final long ccAddr = addr + SDL_GPUColorTargetInfo.CLEAR_COLOR;
        MemoryAccess.putFloat(ccAddr + SDL_FColor.R, 0f);
        MemoryAccess.putFloat(ccAddr + SDL_FColor.G, 0f);
        MemoryAccess.putFloat(ccAddr + SDL_FColor.B, 0f);
        MemoryAccess.putFloat(ccAddr + SDL_FColor.A, 0f);
        return color;
    }

    private long ensureDepthScratch(ContextState st, int width, int height) {
        if (st.readbackDepthScratch == 0L || st.readbackDepthScratchWidth != width || st.readbackDepthScratchHeight != height) {
            if (st.readbackDepthScratch != 0L) resourceManager.releaseTextureDeferred(st.readbackDepthScratch);
            st.readbackDepthScratch = resourceManager.createScratchTexture(DEPTH_TARGET_FORMAT, width, height, SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);
            st.readbackDepthScratchWidth = width;
            st.readbackDepthScratchHeight = height;
        }
        return st.readbackDepthScratch;
    }

    private long ensureStencilScratch(ContextState st, int width, int height) {
        if (st.readbackStencilScratch == 0L || st.readbackStencilScratchWidth != width || st.readbackStencilScratchHeight != height) {
            if (st.readbackStencilScratch != 0L) resourceManager.releaseTextureDeferred(st.readbackStencilScratch);
            st.readbackStencilScratch = resourceManager.createScratchTexture(STENCIL_TARGET_FORMAT, width, height, SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);
            st.readbackStencilScratchWidth = width;
            st.readbackStencilScratchHeight = height;
        }
        return st.readbackStencilScratch;
    }

    private long getDepthPipeline() {
        if (depthPipeline != 0L) return depthPipeline;
        if (!ensureDepthShader()) return 0L;
        buildBit = -1;
        buildDepthFormat = 0;
        depthPipeline = pipelineStore.getOrBuild(Hashing.fmix64(KEY_SEED, -1), builder);
        return depthPipeline;
    }

    private boolean ensureStencilPipelines(int depthFormat) {
        if (depthFormat == 0) return false;
        if (stencilPipelinesFormat == depthFormat) return true;
        if (!ensureStencilShader()) return false;
        buildDepthFormat = depthFormat;
        final long formatKey = Hashing.fmix64(KEY_SEED, depthFormat);
        for (int b = 0; b < STENCIL_BITS; b++) {
            buildBit = b;
            final long pipeline = pipelineStore.getOrBuild(Hashing.fmix64(formatKey, b), builder);
            if (pipeline == 0L) {
                stencilPipelinesFormat = 0;
                return false;
            }
            stencilPipelines[b] = pipeline;
        }
        stencilPipelinesFormat = depthFormat;
        return true;
    }

    private long createDepthPipeline() {
        try (MemoryStack stack = stackPush()) {
            final SDL_GPUColorTargetDescription.Buffer colorDesc = SDL_GPUColorTargetDescription.calloc(1, stack);
            colorDesc.get(0).format(DEPTH_TARGET_FORMAT).blend_state().enable_blend(false);
            final SDL_GPUDepthStencilState depthStencil = SDL_GPUDepthStencilState.calloc(stack)
                .compare_op(SDL_GPU_COMPAREOP_ALWAYS);
            return fullscreen.createPipeline(pipelineStore, stack, depthFragmentShader, colorDesc, 1, depthStencil, 0, "DepthStencilReadback depth");
        }
    }

    private long createStencilPipeline(int bit, int depthFormat) {
        try (MemoryStack stack = stackPush()) {
            final SDL_GPUColorTargetDescription.Buffer colorDesc = SDL_GPUColorTargetDescription.calloc(1, stack);
            colorDesc.get(0).format(STENCIL_TARGET_FORMAT).blend_state()
                .enable_blend(true)
                .src_color_blendfactor(SDL_GPU_BLENDFACTOR_ONE)
                .dst_color_blendfactor(SDL_GPU_BLENDFACTOR_ONE)
                .color_blend_op(SDL_GPU_BLENDOP_ADD)
                .src_alpha_blendfactor(SDL_GPU_BLENDFACTOR_ONE)
                .dst_alpha_blendfactor(SDL_GPU_BLENDFACTOR_ONE)
                .alpha_blend_op(SDL_GPU_BLENDOP_ADD);
            final SDL_GPUDepthStencilState depthStencil = SDL_GPUDepthStencilState.calloc(stack)
                .enable_depth_test(false)
                .enable_depth_write(false)
                .compare_op(SDL_GPU_COMPAREOP_ALWAYS)
                .enable_stencil_test(true)
                .compare_mask((byte) (1 << bit))
                .write_mask((byte) 0);
            depthStencil.front_stencil_state()
                .fail_op(SDL_GPU_STENCILOP_KEEP)
                .pass_op(SDL_GPU_STENCILOP_KEEP)
                .depth_fail_op(SDL_GPU_STENCILOP_KEEP)
                .compare_op(SDL_GPU_COMPAREOP_EQUAL);
            depthStencil.back_stencil_state()
                .fail_op(SDL_GPU_STENCILOP_KEEP)
                .pass_op(SDL_GPU_STENCILOP_KEEP)
                .depth_fail_op(SDL_GPU_STENCILOP_KEEP)
                .compare_op(SDL_GPU_COMPAREOP_EQUAL);
            return fullscreen.createPipeline(pipelineStore, stack, stencilFragmentShader, colorDesc, 1, depthStencil, depthFormat, "DepthStencilReadback stencil");
        }
    }

    private boolean ensureDepthShader() {
        if (depthFragmentShader != 0L) return true;
        if (depthFragmentFailed) return false;
        depthFragmentFailed = true;
        depthFragmentShader = fullscreen.compileFragment(ShaderLoader.getShaderSource(DEPTH_FRAGMENT_SOURCE), -1, "DepthStencilReadback: readback_depth");
        if (depthFragmentShader == 0L) return false;
        depthFragmentFailed = false;
        return true;
    }

    private boolean ensureStencilShader() {
        if (stencilFragmentShader != 0L) return true;
        if (stencilFragmentFailed) return false;
        stencilFragmentFailed = true;
        stencilFragmentShader = fullscreen.compileFragment(ShaderLoader.getShaderSource(STENCIL_FRAGMENT_SOURCE), 16, "DepthStencilReadback: readback_stencil");
        if (stencilFragmentShader == 0L) return false;
        stencilFragmentFailed = false;
        return true;
    }

    private long getNearestSampler() {
        if (nearestSampler != 0L) return nearestSampler;
        try (MemoryStack stack = stackPush()) {
            final SDL_GPUSamplerCreateInfo ci = SDL_GPUSamplerCreateInfo.calloc(stack)
                .min_filter(SDL_GPU_FILTER_NEAREST)
                .mag_filter(SDL_GPU_FILTER_NEAREST)
                .mipmap_mode(SDL_GPU_SAMPLERMIPMAPMODE_NEAREST)
                .address_mode_u(SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE)
                .address_mode_v(SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE)
                .address_mode_w(SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE);
            nearestSampler = SDL_CreateGPUSampler(pipelineStore.device().getDevice(), ci);
            if (nearestSampler == 0L) LOG.error("DepthStencilReadback: sampler creation failed: {}", SDLError.SDL_GetError());
        }
        return nearestSampler;
    }

    public void release(ContextState st) {
        if (st.readbackDepthScratch != 0L) resourceManager.releaseTextureHandle(st.readbackDepthScratch);
        st.readbackDepthScratch = 0L;
        st.readbackDepthScratchWidth = 0;
        st.readbackDepthScratchHeight = 0;
        if (st.readbackStencilScratch != 0L) resourceManager.releaseTextureHandle(st.readbackStencilScratch);
        st.readbackStencilScratch = 0L;
        st.readbackStencilScratchWidth = 0;
        st.readbackStencilScratchHeight = 0;
    }

    public void shutdown() {
        final long dev = pipelineStore.device().getDevice();
        if (dev != 0L) {
            if (depthFragmentShader != 0L) SDL_ReleaseGPUShader(dev, depthFragmentShader);
            if (stencilFragmentShader != 0L) SDL_ReleaseGPUShader(dev, stencilFragmentShader);
            if (fullscreen.vertexShader() != 0L) SDL_ReleaseGPUShader(dev, fullscreen.vertexShader());
            if (nearestSampler != 0L) SDL_ReleaseGPUSampler(dev, nearestSampler);
        }
        fullscreen.shutdown();
        depthFragmentShader = 0L;
        depthFragmentFailed = false;
        stencilFragmentShader = 0L;
        stencilFragmentFailed = false;
        nearestSampler = 0L;
        depthPipeline = 0L;
        Arrays.fill(stencilPipelines, 0L);
        stencilPipelinesFormat = 0;
    }
}
