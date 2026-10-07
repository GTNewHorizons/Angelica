package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.cross.CrossCompileCache;
import com.gtnewhorizons.angelica.sdlgpu.shader.msl.MslCrossCompile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.opengl.GL43;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUComputePipelineCreateInfo;
import org.lwjgl.sdl.SDL_GPUStorageTextureReadWriteBinding;
import org.lwjgl.sdl.SDL_GPUTextureCreateInfo;
import org.lwjgl.sdl.SDL_GPUTextureRegion;
import org.lwjgl.sdl.SDL_GPUTextureTransferInfo;
import org.lwjgl.sdl.SDL_GPUTransferBufferCreateInfo;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.macosx.ObjCRuntime;
import org.lwjgl.util.shaderc.Shaderc;

import static org.lwjgl.sdl.SDLGPU.*;

public final class ImageAtomicsProbe {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final String SOURCE = "angelica:sdlgpu/atomic_probe.csh";
    public static final int MTL_LANGUAGE_VERSION_3_1 = (3 << 16) + 1;
    private static final int GROUPS = 64;

    private final long device;
    private long pipeline;

    public ImageAtomicsProbe(long device) {
        this.device = device;
        final SpirvCompiler.Result compiled = SpirvCompiler.compile(ShaderLoader.getShaderSource(SOURCE), Shaderc.shaderc_compute_shader, "atomic_probe", SpirvCompiler.Options.vulkanForced460Core(), false);
        if (compiled.spirv() == null) {
            LOG.warn("Metal image atomics: probe pipeline failed: {}", compiled.error());
            return;
        }
        CrossCompileCache.Output msl = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ShaderManager.remapSpirvForComputeSDLGPU(compiled.spirv());
            msl = MslCrossCompile.compileAtVersion(compiled.spirv(), GL43.GL_COMPUTE_SHADER, MslCrossCompile.SPVC_MSL_3_1);
            pipeline = SDL_CreateGPUComputePipeline(device, SDL_GPUComputePipelineCreateInfo.calloc(stack)
                .code(msl.code())
                .entrypoint(stack.ASCII(msl.entrypoint()))
                .format(SDL_GPU_SHADERFORMAT_MSL)
                .num_readwrite_storage_textures(1)
                .threadcount_x(InvocationDispatch.WORKGROUP_SIZE).threadcount_y(1).threadcount_z(1));
            if (pipeline == 0) LOG.warn("Metal image atomics: probe pipeline failed: {}", SDLError.SDL_GetError());
        } catch (RuntimeException e) {
            LOG.warn("Metal image atomics: probe pipeline failed", e);
        } finally {
            if (msl != null) MemoryUtil.memFree(msl.code());
            MemoryUtil.memFree(compiled.spirv());
        }
    }

    public static int defaultMslVersion() {
        try {
            final long msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            final long cls = ObjCRuntime.objc_getClass("MTLCompileOptions");
            if (msgSend == 0 || cls == 0) return 0;
            final long options = JNI.invokePPP(cls, ObjCRuntime.sel_getUid("new"), msgSend);
            if (options == 0) return 0;
            final long version = JNI.invokePPP(options, ObjCRuntime.sel_getUid("languageVersion"), msgSend);
            JNI.invokePPV(options, ObjCRuntime.sel_getUid("release"), msgSend);
            return (int) version;
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Could not read the default Metal language version", e);
            return 0;
        }
    }

    public boolean run(int width, int height) {
        if (pipeline == 0) return false;
        long texture = 0, upload = 0, download = 0, cb = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            texture = SDL_CreateGPUTexture(device, SDL_GPUTextureCreateInfo.calloc(stack)
                .type(SDL_GPU_TEXTURETYPE_2D)
                .format(SDL_GPU_TEXTUREFORMAT_R32_INT)
                .usage(SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE)
                .width(width).height(height).layer_count_or_depth(1).num_levels(1)
                .sample_count(SDL_GPU_SAMPLECOUNT_1));
            if (texture == 0) return failSdl(width, height, "texture");

            upload = SDL_CreateGPUTransferBuffer(device, SDL_GPUTransferBufferCreateInfo.calloc(stack).usage(SDL_GPU_TRANSFERBUFFERUSAGE_UPLOAD).size(Integer.BYTES));
            download = SDL_CreateGPUTransferBuffer(device, SDL_GPUTransferBufferCreateInfo.calloc(stack).usage(SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD).size(Integer.BYTES));
            if (upload == 0 || download == 0) return failSdl(width, height, "transfer buffer");
            final long zero = nSDL_MapGPUTransferBuffer(device, upload, false);
            if (zero == 0) return failSdl(width, height, "map");
            MemoryUtil.memPutInt(zero, 0);
            SDL_UnmapGPUTransferBuffer(device, upload);

            cb = SDL_AcquireGPUCommandBuffer(device);
            if (cb == 0) return failSdl(width, height, "command buffer");
            final SDL_GPUTextureRegion region = SDL_GPUTextureRegion.calloc(stack).texture(texture).w(1).h(1).d(1);
            long copy = SDL_BeginGPUCopyPass(cb);
            SDL_UploadToGPUTexture(copy, SDL_GPUTextureTransferInfo.calloc(stack).transfer_buffer(upload), region, false);
            SDL_EndGPUCopyPass(copy);

            final SDL_GPUStorageTextureReadWriteBinding.Buffer binding = SDL_GPUStorageTextureReadWriteBinding.calloc(1, stack);
            binding.get(0).texture(texture);
            final long pass = SDL_BeginGPUComputePass(cb, binding, null);
            if (pass == 0) return failSdl(width, height, "compute pass");
            SDL_BindGPUComputePipeline(pass, pipeline);
            SDL_DispatchGPUCompute(pass, GROUPS, 1, 1);
            SDL_EndGPUComputePass(pass);

            copy = SDL_BeginGPUCopyPass(cb);
            SDL_DownloadFromGPUTexture(copy, region, SDL_GPUTextureTransferInfo.calloc(stack).transfer_buffer(download));
            SDL_EndGPUCopyPass(copy);
            final boolean submitted = SDL_SubmitGPUCommandBuffer(cb);
            cb = 0;
            if (!submitted) return failSdl(width, height, "submit");
            SDL_WaitForGPUIdle(device);

            final long result = nSDL_MapGPUTransferBuffer(device, download, false);
            if (result == 0) return failSdl(width, height, "map");
            final int adds = MemoryUtil.memGetInt(result);
            SDL_UnmapGPUTransferBuffer(device, download);

            final int invocations = GROUPS * InvocationDispatch.WORKGROUP_SIZE;
            if (adds != invocations) LOG.info("Metal image atomics on {}x{}: {} invocations added {}", width, height, invocations, adds);
            return adds == invocations;
        } catch (RuntimeException e) {
            return fail(width, height, e.toString());
        } finally {
            if (cb != 0) SDL_CancelGPUCommandBuffer(cb);
            if (download != 0) SDL_ReleaseGPUTransferBuffer(device, download);
            if (upload != 0) SDL_ReleaseGPUTransferBuffer(device, upload);
            if (texture != 0) SDL_ReleaseGPUTexture(device, texture);
        }
    }

    public void release() {
        if (pipeline != 0) SDL_ReleaseGPUComputePipeline(device, pipeline);
        pipeline = 0;
    }

    private static boolean failSdl(int width, int height, String what) {
        return fail(width, height, what + " failed: " + SDLError.SDL_GetError());
    }

    private static boolean fail(int width, int height, String reason) {
        LOG.warn("Metal image atomics on {}x{}: {}", width, height, reason);
        return false;
    }
}
