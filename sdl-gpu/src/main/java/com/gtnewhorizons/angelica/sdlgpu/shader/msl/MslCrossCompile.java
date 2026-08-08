package com.gtnewhorizons.angelica.sdlgpu.shader.msl;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.sdlgpu.shader.cross.CrossCompileUtil;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcMslResourceBinding2;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memUTF8;

public final class MslCrossCompile {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");
    private static final String BACKEND = "MSL";

    public record Output(ByteBuffer code, String entrypoint) {}

    private static final int CACHE_MAX = 256;
    private static final Object2ObjectLinkedOpenHashMap<CacheKey, CacheValue> CACHE = new Object2ObjectLinkedOpenHashMap<>();

    private static final class CacheKey {
        public final byte[] spirvBytes;
        public final int glShaderType;
        public final int hash;
        public CacheKey(byte[] b, int t) {
            this.spirvBytes = b;
            this.glShaderType = t;
            this.hash = Arrays.hashCode(b) * 31 + t;
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof CacheKey k)) return false;
            return k.glShaderType == glShaderType && k.hash == hash && Arrays.equals(k.spirvBytes, spirvBytes);
        }
    }

    private record CacheValue(byte[] codeBytes, String entrypoint) {}

    public static void clearCache() {
        synchronized (CACHE) { CACHE.clear(); }
    }

    @FunctionalInterface
    private interface BufferSlotFn { public int slot(int set, int binding); }

    @FunctionalInterface
    private interface IntSlotFn { public int slot(int binding); }

    private MslCrossCompile() {}

    public static Output compile(ByteBuffer spirv, int glShaderType) {
        final int executionModel;
        if (glShaderType == GL20.GL_VERTEX_SHADER) {
            executionModel = Spv.SpvExecutionModelVertex;
        } else if (glShaderType == GL20.GL_FRAGMENT_SHADER) {
            executionModel = Spv.SpvExecutionModelFragment;
        } else if (glShaderType == GL43.GL_COMPUTE_SHADER) {
            executionModel = Spv.SpvExecutionModelGLCompute;
        } else {
            throw new UnsupportedOperationException("Unsupported shader type for MSL cross-compile: 0x" + Integer.toHexString(glShaderType));
        }

        final byte[] spirvHeap = new byte[spirv.remaining()];
        spirv.duplicate().get(spirvHeap);
        final CacheKey key = new CacheKey(spirvHeap, glShaderType);
        final CacheValue cached;
        synchronized (CACHE) {
            cached = CACHE.getAndMoveToFirst(key);
        }
        if (cached != null) {
            final ByteBuffer copy = memAlloc(cached.codeBytes.length);
            copy.put(cached.codeBytes).flip();
            return new Output(copy, cached.entrypoint);
        }

        final int dumpId = SystemProperties.DUMP_SHADERS ? CrossCompileUtil.SHADER_DUMP_COUNTER.getAndIncrement() : -1;
        if (dumpId >= 0) CrossCompileUtil.dumpSpirv(spirv, dumpId, glShaderType);

        try (MemoryStack stack = stackPush()) {
            final PointerBuffer pContext = stack.pointers(0);
            if (Spvc.spvc_context_create(pContext) != Spvc.SPVC_SUCCESS) {
                throw new RuntimeException("MSL cross-compile failed: spvc_context_create");
            }
            final long ctx = pContext.get(0);
            try {
                final IntBuffer spirvWords = spirv.asIntBuffer();
                final PointerBuffer pParsedIR = stack.pointers(0);
                if (Spvc.spvc_context_parse_spirv(ctx, spirvWords, spirvWords.remaining(), pParsedIR) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "parse_spirv");
                }

                final PointerBuffer pCompiler = stack.pointers(0);
                if (Spvc.spvc_context_create_compiler(ctx, Spvc.SPVC_BACKEND_MSL, pParsedIR.get(0),
                        Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pCompiler) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "create_compiler(MSL)");
                }
                final long compiler = pCompiler.get(0);

                final PointerBuffer pOpts = stack.pointers(0);
                if (Spvc.spvc_compiler_create_compiler_options(compiler, pOpts) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "create_compiler_options");
                }
                final long opts = pOpts.get(0);
                Spvc.spvc_compiler_options_set_uint(opts, Spvc.SPVC_COMPILER_OPTION_MSL_VERSION, mslVersion(2, 2, 0));
                Spvc.spvc_compiler_options_set_uint(opts, Spvc.SPVC_COMPILER_OPTION_MSL_PLATFORM, Spvc.SPVC_MSL_PLATFORM_MACOS);
                if (Spvc.spvc_compiler_install_compiler_options(compiler, opts) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "install_compiler_options");
                }

                final PointerBuffer pResources = stack.pointers(0);
                if (Spvc.spvc_compiler_create_shader_resources(compiler, pResources) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "create_shader_resources");
                }
                final long resources = pResources.get(0);

                if (glShaderType == GL43.GL_COMPUTE_SHADER) {
                    addComputeBindings(compiler, resources, stack);
                } else {
                    addGraphicsBindings(compiler, resources, executionModel, stack);
                }

                final PointerBuffer pSrc = stack.pointers(0);
                if (Spvc.spvc_compiler_compile(compiler, pSrc) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "compile");
                }
                final String mslSrc = memUTF8(pSrc.get(0));
                if (dumpId >= 0) CrossCompileUtil.dumpText(mslSrc, dumpId, glShaderType, "msl");
                final ByteBuffer code = memUTF8(mslSrc, true);
                final String rawEntry = Spvc.spvc_compiler_get_cleansed_entry_point_name(compiler, "main", executionModel);
                final String entry = rawEntry != null ? rawEntry : "main0";
                final byte[] codeHeap = new byte[code.remaining()];
                code.duplicate().get(codeHeap);
                synchronized (CACHE) {
                    CACHE.putAndMoveToFirst(key, new CacheValue(codeHeap, entry));
                    while (CACHE.size() > CACHE_MAX) CACHE.removeLast();
                }
                return new Output(code, entry);
            } finally {
                Spvc.spvc_context_destroy(ctx);
            }
        }
    }

    private static int mslVersion(int major, int minor, int patch) {
        return (major * 10000) + (minor * 100) + patch;
    }

    private static void addGraphicsBindings(long compiler, long resources, int executionModel, MemoryStack stack) {
        final int numUBOs = CrossCompileUtil.countResources(resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, stack);

        addTextureBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE, executionModel, stack);
        addTextureBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE, executionModel, stack);
        addTextureBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE, executionModel, stack);

        addBufferBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, executionModel, stack, (set, binding) -> binding);

        final int[] ssboIdx = {0};
        addBufferBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, executionModel, stack, (set, binding) -> numUBOs + ssboIdx[0]++);
    }

    private static int addTextureBindings(long compiler, long resources, int resourceType, int executionModel, MemoryStack stack) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) {
            return 0;
        }
        final int count = (int) pCount.get(0);
        if (count == 0) return 0;

        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        for (int i = 0; i < count; i++) {
            final SpvcReflectedResource res = list.get(i);
            final int spvId = res.id();
            final int set = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationDescriptorSet);
            final int binding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);

            final SpvcMslResourceBinding2 b = SpvcMslResourceBinding2.calloc(stack);
            Spvc.spvc_msl_resource_binding_init_2(b);
            b.stage(executionModel)
             .desc_set(set)
             .binding(binding)
             .count(1)
             .msl_texture(binding)
             .msl_sampler(binding);
            if (Spvc.spvc_compiler_msl_add_resource_binding_2(compiler, b) != Spvc.SPVC_SUCCESS) {
                LOG.warn("spvc_compiler_msl_add_resource_binding_2 failed for texture id={} set={} binding={}", spvId, set, binding);
            }
        }
        return count;
    }

    private static void addBufferBindings(long compiler, long resources, int resourceType, int executionModel, MemoryStack stack, BufferSlotFn slotFn) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) {
            return;
        }
        final int count = (int) pCount.get(0);
        if (count == 0) return;

        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        for (int i = 0; i < count; i++) {
            final SpvcReflectedResource res = list.get(i);
            final int spvId = res.id();
            final int set = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationDescriptorSet);
            final int binding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);

            final SpvcMslResourceBinding2 b = SpvcMslResourceBinding2.calloc(stack);
            Spvc.spvc_msl_resource_binding_init_2(b);
            b.stage(executionModel)
             .desc_set(set)
             .binding(binding)
             .count(1)
             .msl_buffer(slotFn.slot(set, binding));
            if (Spvc.spvc_compiler_msl_add_resource_binding_2(compiler, b) != Spvc.SPVC_SUCCESS) {
                LOG.warn("spvc_compiler_msl_add_resource_binding_2 failed for buffer id={} set={} binding={}", spvId, set, binding);
            }
        }
    }

    private static void addComputeBindings(long compiler, long resources, MemoryStack stack) {
        final int execModel = Spv.SpvExecutionModelGLCompute;

        final int numSamplers = CrossCompileUtil.countResources(resources, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE, stack);
        final int[] roRwTex   = CrossCompileUtil.countStorageImagesSplit(resources, compiler, stack);
        final int[] roRwBuf   = CrossCompileUtil.countStorageBuffersSplit(resources, compiler, stack);
        final int numROTex = roRwTex[0];
        final int numROBuf = roRwBuf[0];
        final int numRWBuf = roRwBuf[1];
        final int numUBOs  = CrossCompileUtil.countResources(resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, stack);

        addComputeTextureBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE,  execModel, stack, binding -> binding, /*hasSampler*/ true);
        addComputeTextureBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE, execModel, stack, binding -> binding, false);

        final int auxBufStart = numUBOs + numROBuf + numRWBuf;
        addComputeStorageImageBindings(compiler, resources, execModel, stack,
            /*roSlot*/ binding -> binding,
            /*rwSlot*/ binding -> numSamplers + numROTex + binding,
            auxBufStart);

        addComputeBufferBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, execModel, stack, (set, binding) -> binding);

        final int[] roSsboIdx = {0};
        final int[] rwSsboIdx = {0};
        addComputeStorageBufferBindings(compiler, resources, execModel, stack, /*roSlot*/ binding -> numUBOs + roSsboIdx[0]++, /*rwSlot*/ binding -> numUBOs + numROBuf + rwSsboIdx[0]++);
    }

    private static void addComputeTextureBindings(long compiler, long resources, int resourceType, int executionModel, MemoryStack stack, IntSlotFn slotFn, boolean hasSampler) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) return;
        final int count = (int) pCount.get(0);
        if (count == 0) return;

        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        for (int i = 0; i < count; i++) {
            final SpvcReflectedResource res = list.get(i);
            final int spvId = res.id();
            final int set = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationDescriptorSet);
            final int binding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);
            final int mslSlot = slotFn.slot(binding);

            final SpvcMslResourceBinding2 b = SpvcMslResourceBinding2.calloc(stack);
            Spvc.spvc_msl_resource_binding_init_2(b);
            b.stage(executionModel)
             .desc_set(set)
             .binding(binding)
             .count(1)
             .msl_texture(mslSlot);
            if (hasSampler) b.msl_sampler(mslSlot);
            if (Spvc.spvc_compiler_msl_add_resource_binding_2(compiler, b) != Spvc.SPVC_SUCCESS) {
                LOG.warn("spvc_compiler_msl_add_resource_binding_2 failed for compute texture id={} set={} binding={}", spvId, set, binding);
            }
        }
    }

    private static void addComputeStorageImageBindings(long compiler, long resources, int executionModel, MemoryStack stack, IntSlotFn roSlot, IntSlotFn rwSlot, int auxBufStart) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE, pList, pCount) != Spvc.SPVC_SUCCESS) return;
        final int count = (int) pCount.get(0);
        if (count == 0) return;

        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        int auxIdx = 0;
        for (int i = 0; i < count; i++) {
            final SpvcReflectedResource res = list.get(i);
            final int spvId = res.id();
            final int set = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationDescriptorSet);
            final int binding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);
            final boolean readOnly = Spvc.spvc_compiler_has_decoration(compiler, spvId, Spv.SpvDecorationNonWritable);
            final int mslSlot = readOnly ? roSlot.slot(binding) : rwSlot.slot(binding);

            final SpvcMslResourceBinding2 b = SpvcMslResourceBinding2.calloc(stack);
            Spvc.spvc_msl_resource_binding_init_2(b);
            b.stage(executionModel)
             .desc_set(set)
             .binding(binding)
             .count(1)
             .msl_texture(mslSlot)
             .msl_buffer(auxBufStart + auxIdx++);
            if (Spvc.spvc_compiler_msl_add_resource_binding_2(compiler, b) != Spvc.SPVC_SUCCESS) {
                LOG.warn("spvc_compiler_msl_add_resource_binding_2 failed for compute storage image id={} set={} binding={} ro={}", spvId, set, binding, readOnly);
            }
        }
    }

    private static void addComputeBufferBindings(long compiler, long resources, int resourceType, int executionModel, MemoryStack stack, BufferSlotFn slotFn) {
        addBufferBindings(compiler, resources, resourceType, executionModel, stack, slotFn);
    }

    private static void addComputeStorageBufferBindings(long compiler, long resources, int executionModel, MemoryStack stack, IntSlotFn roSlot, IntSlotFn rwSlot) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, pList, pCount) != Spvc.SPVC_SUCCESS) return;
        final int count = (int) pCount.get(0);
        if (count == 0) return;

        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        for (int i = 0; i < count; i++) {
            final SpvcReflectedResource res = list.get(i);
            final int spvId = res.id();
            final int set = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationDescriptorSet);
            final int binding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);
            final boolean readOnly = CrossCompileUtil.isStorageBufferReadOnly(compiler, spvId, stack);
            final int mslSlot = readOnly ? roSlot.slot(binding) : rwSlot.slot(binding);

            final SpvcMslResourceBinding2 b = SpvcMslResourceBinding2.calloc(stack);
            Spvc.spvc_msl_resource_binding_init_2(b);
            b.stage(executionModel)
             .desc_set(set)
             .binding(binding)
             .count(1)
             .msl_buffer(mslSlot);
            if (Spvc.spvc_compiler_msl_add_resource_binding_2(compiler, b) != Spvc.SPVC_SUCCESS) {
                LOG.warn("spvc_compiler_msl_add_resource_binding_2 failed for compute storage buffer id={} set={} binding={} ro={}", spvId, set, binding, readOnly);
            }
        }
    }
}
