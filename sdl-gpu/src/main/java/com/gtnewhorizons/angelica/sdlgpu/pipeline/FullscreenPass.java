package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
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
import org.lwjgl.sdl.SDL_GPUSamplerCreateInfo;
import org.lwjgl.sdl.SDL_GPUVertexInputState;
import org.lwjgl.sdl.SDL_GPUViewport;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.lwjgl.sdl.SDLGPU.*;

/** Shared fullscreen shaders and sampler, owned and released by the backend. */
public final class FullscreenPass {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final String VERTEX_SOURCE = "angelica:sdlgpu/clear_attachment.vsh";

    private final ShaderManager shaderManager;
    private final Device device;
    private final List<Fragment> fragments = new ArrayList<>();
    private int vertexShaderId;
    private long sdlVertexShader;
    private long nearestSampler;
    private boolean samplerAttempted;
    private boolean vertexFailed;

    public FullscreenPass(ShaderManager shaderManager, Device device) {
        this.shaderManager = shaderManager;
        this.device = device;
    }

    final class Fragment {
        private final String label;
        private final Supplier<String> source;
        private final int requiredUboSize;
        private boolean attempted;
        private long shader;

        private Fragment(String label, Supplier<String> source, int requiredUboSize) {
            this.label = label;
            this.source = source;
            this.requiredUboSize = requiredUboSize;
        }

        long shader() {
            if (!attempted) {
                attempted = true;
                shader = compileFragment(source.get(), requiredUboSize, label);
            }
            return shader;
        }
    }

    Fragment fragment(String path, int requiredUboSize) {
        return fragment(path, requiredUboSize, () -> ShaderLoader.getShaderSource(path));
    }

    Fragment fragment(String label, int requiredUboSize, Supplier<String> source) {
        final Fragment fragment = new Fragment(label, source, requiredUboSize);
        fragments.add(fragment);
        return fragment;
    }

    long getNearestSampler() {
        if (samplerAttempted) return nearestSampler;
        samplerAttempted = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final SDL_GPUSamplerCreateInfo ci = SDL_GPUSamplerCreateInfo.calloc(stack)
                .min_filter(SDL_GPU_FILTER_NEAREST)
                .mag_filter(SDL_GPU_FILTER_NEAREST)
                .mipmap_mode(SDL_GPU_SAMPLERMIPMAPMODE_NEAREST)
                .address_mode_u(SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE)
                .address_mode_v(SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE)
                .address_mode_w(SDL_GPU_SAMPLERADDRESSMODE_CLAMP_TO_EDGE);
            nearestSampler = SDL_CreateGPUSampler(device.getDevice(), ci);
            if (nearestSampler == 0L) LOG.error("FullscreenPass: sampler creation failed: {}", SDLError.SDL_GetError());
        }
        return nearestSampler;
    }

    private long compileFragment(String fragmentSource, int requiredUboSize, String label) {
        if (vertexFailed) return 0;
        if (vertexShaderId == 0) {
            vertexShaderId = shaderManager.createShader(GL20.GL_VERTEX_SHADER);
            shaderManager.shaderSource(vertexShaderId, ShaderLoader.getShaderSource(VERTEX_SOURCE));
            shaderManager.compileShader(vertexShaderId);
        }

        final int fs = shaderManager.createShader(GL20.GL_FRAGMENT_SHADER);
        final int prog = shaderManager.createProgram();
        try {
            shaderManager.shaderSource(fs, fragmentSource);
            shaderManager.compileShader(fs);
            shaderManager.attachShader(prog, vertexShaderId);
            shaderManager.attachShader(prog, fs);
            shaderManager.linkProgram(prog);

            final ShaderManager.ProgramObject po = shaderManager.getProgram(prog);
            if (po == null || !po.linked) {
                vertexFailed = !shaderManager.isCompiled(vertexShaderId);
                LOG.error("{}: program failed to link: {}", label, po == null ? "no program object" : po.infoLog);
                return 0;
            }
            if (requiredUboSize >= 0 && po.fragmentUboSize != requiredUboSize) {
                LOG.error("{}: fragment UBO size {} != {}", label, po.fragmentUboSize, requiredUboSize);
                return 0;
            }

            if (sdlVertexShader == 0) {
                sdlVertexShader = shaderManager.createSDLShader(po.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, po.vertexResources.numSamplers(), po.vertexResources.numUBOs(), po.vertexResources.numStorageBuffers(), po.vertexResources.numStorageTextures());
                if (sdlVertexShader == 0) {
                    vertexFailed = true;
                    LOG.error("{}: vertex SDL shader creation failed", label);
                    return 0;
                }
            }

            final long fragmentShader = shaderManager.createSDLShader(po.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, po.fragmentResources.numSamplers(), po.fragmentResources.numUBOs(), po.fragmentResources.numStorageBuffers(), po.fragmentResources.numStorageTextures());
            if (fragmentShader == 0) LOG.error("{}: fragment SDL shader creation failed", label);
            return fragmentShader;
        } finally {
            shaderManager.releaseProgramFinal(prog);
            shaderManager.deleteShader(fs);
        }
    }

    long createPipeline(PipelineStore pipelineStore, MemoryStack stack, long fragmentShader, SDL_GPUColorTargetDescription.Buffer colorDesc, int numColorTargets, SDL_GPUDepthStencilState depthStencil, int depthFormat, String label) {
        final SDL_GPURasterizerState rasterizer = SDL_GPURasterizerState.calloc(stack)
            .cull_mode(SDL_GPU_CULLMODE_NONE)
            .front_face(SDL_GPU_FRONTFACE_COUNTER_CLOCKWISE)
            .fill_mode(SDL_GPU_FILLMODE_FILL);

        final SDL_GPUMultisampleState multisample = SDL_GPUMultisampleState.calloc(stack)
            .sample_count(SDL_GPU_SAMPLECOUNT_1);

        final SDL_GPUGraphicsPipelineTargetInfo targetInfo = SDL_GPUGraphicsPipelineTargetInfo.calloc(stack)
            .num_color_targets(numColorTargets)
            .color_target_descriptions(colorDesc)
            .depth_stencil_format(depthFormat)
            .has_depth_stencil_target(depthFormat != 0);

        final SDL_GPUVertexInputState vertexInput = SDL_GPUVertexInputState.calloc(stack);

        final SDL_GPUGraphicsPipelineCreateInfo ci = SDL_GPUGraphicsPipelineCreateInfo.calloc(stack)
            .vertex_shader(sdlVertexShader)
            .fragment_shader(fragmentShader)
            .primitive_type(SDL_GPU_PRIMITIVETYPE_TRIANGLELIST)
            .rasterizer_state(rasterizer)
            .multisample_state(multisample)
            .depth_stencil_state(depthStencil)
            .target_info(targetInfo)
            .vertex_input_state(vertexInput);

        final long pipeline = SDL_CreateGPUGraphicsPipeline(pipelineStore.device().getDevice(), ci);
        if (pipeline == 0) {
            LOG.error("{}: pipeline creation failed: {}", label, SDLError.SDL_GetError());
            return PipelineStore.BAD_PIPELINE_SENTINEL;
        }
        return pipeline;
    }

    static void setViewport(long renderPass, ContextState st, int width, int height, float depth) {
        final long vpAddr = st.cachedViewport.address();
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.X, 0f);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.Y, 0f);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.W, width);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.H, height);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.MIN_DEPTH, depth);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.MAX_DEPTH, depth);
        SDL_SetGPUViewport(renderPass, st.cachedViewport);
    }

    static void setScissor(long renderPass, ContextState st, int x, int y, int width, int height) {
        final long scAddr = st.cachedScissor.address();
        MemoryAccess.putInt(scAddr + SDL_Rect.X, x);
        MemoryAccess.putInt(scAddr + SDL_Rect.Y, y);
        MemoryAccess.putInt(scAddr + SDL_Rect.W, width);
        MemoryAccess.putInt(scAddr + SDL_Rect.H, height);
        SDL_SetGPUScissor(renderPass, st.cachedScissor);
    }

    static void markStateClobbered(ContextState st) {
        st.lastBoundPipeline = 0;
        st.viewportDirty = true;
        st.scissorDirty = true;
        st.lastAppliedStencilRef = Integer.MIN_VALUE;
        st.lastAppliedVboBindCb = 0;
    }

    public void shutdown() {
        final long dev = device.getDevice();
        if (dev != 0L) {
            for (Fragment fragment : fragments) {
                if (fragment.shader != 0L) SDL_ReleaseGPUShader(dev, fragment.shader);
            }
            if (sdlVertexShader != 0L) SDL_ReleaseGPUShader(dev, sdlVertexShader);
            if (nearestSampler != 0L) SDL_ReleaseGPUSampler(dev, nearestSampler);
        }
        for (Fragment fragment : fragments) {
            fragment.shader = 0;
            fragment.attempted = false;
        }
        if (vertexShaderId != 0) shaderManager.deleteShader(vertexShaderId);
        vertexShaderId = 0;
        sdlVertexShader = 0;
        nearestSampler = 0;
        samplerAttempted = false;
        vertexFailed = false;
    }
}
