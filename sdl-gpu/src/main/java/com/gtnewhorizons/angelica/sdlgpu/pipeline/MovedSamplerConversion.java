package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import org.lwjgl.sdl.SDL_GPUColorTargetDescription;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilState;
import org.lwjgl.sdl.SDL_GPUTextureSamplerBinding;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Writes a texture SDL cannot give storage-read usage (depth, sRGB) into one it can.
 */
public final class MovedSamplerConversion {

    private static final String FRAGMENT_SOURCE = "angelica:sdlgpu/moved_sampler_convert.fsh";
    private static final long KEY_SEED = 0x6A09E667F3BCC908L;

    private final FrameManager frameManager;
    private final PipelineStore pipelineStore;
    private final FullscreenPass fullscreen;
    private final FullscreenPass.Fragment fragment;
    private final LongSupplier builder = this::createPipeline;
    private int buildFormat;

    public MovedSamplerConversion(FrameManager frameManager, PipelineStore pipelineStore, FullscreenPass fullscreen) {
        this.frameManager = frameManager;
        this.pipelineStore = pipelineStore;
        this.fullscreen = fullscreen;
        fragment = fullscreen.fragment(FRAGMENT_SOURCE, 16);
    }

    public boolean convert(ContextState st, long source, long target, int targetFormat, int width, int height, int levels) {
        final FrameState f = frameManager.frame();
        if (!f.frameActive) return false;
        final long pipeline = pipelineFor(targetFormat);
        final long sampler = fullscreen.getNearestSampler();
        if (pipeline == 0L || sampler == 0L) return false;

        try (MemoryStack stack = stackPush()) {
            final SDL_GPUColorTargetInfo.Buffer color = SDL_GPUColorTargetInfo.calloc(1, stack);
            final SDL_GPUTextureSamplerBinding.Buffer binding = SDL_GPUTextureSamplerBinding.calloc(1, stack);
            binding.get(0).texture(source).sampler(sampler);
            final ByteBuffer level = stack.calloc(16);
            for (int l = 0; l < Math.max(1, levels); l++) {
                final int w = Math.max(1, width >> l);
                final int h = Math.max(1, height >> l);
                color.get(0).texture(target).mip_level(l).load_op(SDL_GPU_LOADOP_DONT_CARE).store_op(SDL_GPU_STOREOP_STORE);
                final long rp = frameManager.beginRenderPass(color, null);
                SDL_BindGPUGraphicsPipeline(rp, pipeline);
                FullscreenPass.setViewport(rp, st, w, h, 0f);
                FullscreenPass.setScissor(rp, st, 0, 0, w, h);
                SDL_BindGPUFragmentSamplers(rp, 0, binding);
                level.putInt(0, l);
                SDL_PushGPUFragmentUniformData(frameManager.getCommandBuffer(f), 0, level);
                SDL_DrawGPUPrimitives(rp, 3, 1, 0, 0);
                frameManager.endRenderPassIfActive(f, FrameManager.PASS_END_COPY);
            }
        }
        FullscreenPass.markStateClobbered(st);
        st.lastPushedProgramFs = -1;
        return true;
    }

    private long pipelineFor(int targetFormat) {
        if (fragment.shader() == 0L) return 0L;
        buildFormat = targetFormat;
        return pipelineStore.getOrBuild(Hashing.fmix64(KEY_SEED, targetFormat), builder);
    }

    private long createPipeline() {
        try (MemoryStack stack = stackPush()) {
            final SDL_GPUColorTargetDescription.Buffer colorDesc = SDL_GPUColorTargetDescription.calloc(1, stack);
            colorDesc.get(0).format(buildFormat).blend_state().enable_blend(false);
            final SDL_GPUDepthStencilState depthStencil = SDL_GPUDepthStencilState.calloc(stack).compare_op(SDL_GPU_COMPAREOP_ALWAYS);
            return fullscreen.createPipeline(pipelineStore, stack, fragment.shader(), colorDesc, 1, depthStencil, 0, "MovedSamplerConversion");
        }
    }
}
