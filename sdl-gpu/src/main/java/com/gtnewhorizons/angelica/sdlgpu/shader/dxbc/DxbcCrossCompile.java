package com.gtnewhorizons.angelica.sdlgpu.shader.dxbc;

import org.lwjgl.system.MemoryUtil;
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
import org.lwjgl.util.spvc.SpvcHLSLResourceBinding;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memByteBufferNT1;
import static org.lwjgl.system.MemoryUtil.memUTF8;

public final class DxbcCrossCompile {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");
    private static final String BACKEND = "DXBC";

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

    private DxbcCrossCompile() {}

    public static Output compile(ByteBuffer spirv, int glShaderType) {
        final boolean isCompute = (glShaderType == GL43.GL_COMPUTE_SHADER);
        final String target;
        if (glShaderType == GL20.GL_VERTEX_SHADER) target = "vs_5_1";
        else if (glShaderType == GL20.GL_FRAGMENT_SHADER) target = "ps_5_1";
        else if (isCompute) target = "cs_5_1";
        else throw new UnsupportedOperationException("Unsupported shader type for DXBC cross-compile: 0x" + Integer.toHexString(glShaderType));

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

        final int dumpId = SystemProperties.dumpShaders() ? CrossCompileUtil.SHADER_DUMP_COUNTER.getAndIncrement() : -1;
        if (dumpId >= 0) CrossCompileUtil.dumpSpirv(spirv, dumpId, glShaderType);

        final String hlsl = compileToHlsl(spirv, glShaderType);
        if (dumpId >= 0) CrossCompileUtil.dumpText(hlsl, dumpId, glShaderType, "hlsl");

        final ByteBuffer hlslNative = memUTF8(hlsl, false);
        try {
            final ByteBuffer dxbc = D3DCompiler.compile(memAddress(hlslNative), hlslNative.remaining(), "main", target, D3DCompiler.D3DCOMPILE_OPTIMIZATION_LEVEL3 | D3DCompiler.D3DCOMPILE_ENABLE_STRICTNESS, 0);
            if (dumpId >= 0) CrossCompileUtil.dumpBytes(dxbc, dumpId, glShaderType, "dxbc");
            final byte[] dxbcHeap = new byte[dxbc.remaining()];
            dxbc.duplicate().get(dxbcHeap);
            synchronized (CACHE) {
                CACHE.putAndMoveToFirst(key, new CacheValue(dxbcHeap, "main"));
                while (CACHE.size() > CACHE_MAX) CACHE.removeLast();
            }
            return new Output(dxbc, "main");
        } finally {
            MemoryUtil.memFree(hlslNative);
        }
    }

    static String compileToHlsl(ByteBuffer spirv, int glShaderType) {
        final boolean isCompute = (glShaderType == GL43.GL_COMPUTE_SHADER);
        try (MemoryStack stack = stackPush()) {
            final PointerBuffer pContext = stack.pointers(0);
            if (Spvc.spvc_context_create(pContext) != Spvc.SPVC_SUCCESS) {
                throw new RuntimeException("DXBC cross-compile failed: spvc_context_create");
            }
            final long ctx = pContext.get(0);
            try {
                final IntBuffer spirvWords = spirv.asIntBuffer();
                final PointerBuffer pParsedIR = stack.pointers(0);
                if (Spvc.spvc_context_parse_spirv(ctx, spirvWords, spirvWords.remaining(), pParsedIR) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "parse_spirv");
                }

                final PointerBuffer pCompiler = stack.pointers(0);
                if (Spvc.spvc_context_create_compiler(ctx, Spvc.SPVC_BACKEND_HLSL, pParsedIR.get(0), Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pCompiler) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "create_compiler(HLSL)");
                }
                final long compiler = pCompiler.get(0);

                final PointerBuffer pOpts = stack.pointers(0);
                if (Spvc.spvc_compiler_create_compiler_options(compiler, pOpts) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "create_compiler_options");
                }
                final long opts = pOpts.get(0);
                Spvc.spvc_compiler_options_set_uint(opts, Spvc.SPVC_COMPILER_OPTION_HLSL_SHADER_MODEL, 51);
                Spvc.spvc_compiler_options_set_uint(opts, Spvc.SPVC_COMPILER_OPTION_HLSL_POINT_COORD_COMPAT, 1);
                Spvc.spvc_compiler_options_set_uint(opts, Spvc.SPVC_COMPILER_OPTION_HLSL_POINT_SIZE_COMPAT, 1);
                Spvc.spvc_compiler_options_set_uint(opts, Spvc.SPVC_COMPILER_OPTION_HLSL_USE_ENTRY_POINT_NAME, 1);
                Spvc.spvc_compiler_options_set_uint(opts, Spvc.SPVC_COMPILER_OPTION_HLSL_NONWRITABLE_UAV_TEXTURE_AS_SRV, 1);
                if (Spvc.spvc_compiler_install_compiler_options(compiler, opts) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "install_compiler_options");
                }

                final PointerBuffer pResources = stack.pointers(0);
                if (Spvc.spvc_compiler_create_shader_resources(compiler, pResources) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "create_shader_resources");
                }
                final long resources = pResources.get(0);

                addStageBindings(compiler, resources, layoutFor(glShaderType), stack);

                final PointerBuffer pSrc = stack.pointers(0);
                if (Spvc.spvc_compiler_compile(compiler, pSrc) != Spvc.SPVC_SUCCESS) {
                    throw CrossCompileUtil.spvcError(ctx, BACKEND, "compile");
                }
                return memUTF8(memByteBufferNT1(pSrc.get(0)));
            } finally {
                Spvc.spvc_context_destroy(ctx);
            }
        }
    }

    private record StageBindingLayout(int execModel, int srvSpace, int uboSpace, int uavSpace) {}

    private static final StageBindingLayout VS = new StageBindingLayout(Spv.SpvExecutionModelVertex,    0, 1, -1);
    private static final StageBindingLayout FS = new StageBindingLayout(Spv.SpvExecutionModelFragment,  2, 3, -1);
    private static final StageBindingLayout CS = new StageBindingLayout(Spv.SpvExecutionModelGLCompute, 0, 2,  1);

    private static StageBindingLayout layoutFor(int glShaderType) {
        return switch (glShaderType) {
            case GL20.GL_VERTEX_SHADER   -> VS;
            case GL20.GL_FRAGMENT_SHADER -> FS;
            case GL43.GL_COMPUTE_SHADER  -> CS;
            default -> throw new UnsupportedOperationException("Unsupported shader type: 0x" + Integer.toHexString(glShaderType));
        };
    }

    private enum ResourceClass { SRV_WITH_SAMPLER, SAMPLER_ONLY, CBV }

    @FunctionalInterface
    private interface ReadOnlyClassifier {
        boolean isReadOnly(long compiler, int spvId, MemoryStack stack);
    }

    private static void addStageBindings(long compiler, long resources, StageBindingLayout layout, MemoryStack stack) {
        addBindingsForType(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE, layout, stack, ResourceClass.SRV_WITH_SAMPLER, layout.srvSpace);
        addBindingsForType(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_SAMPLERS, layout, stack, ResourceClass.SAMPLER_ONLY,     layout.srvSpace);
        addBindingsForType(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, layout, stack, ResourceClass.CBV,              layout.uboSpace);
        addStorageBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE, layout, stack, (c, id, st) -> true);
        addStorageBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE,  layout, stack, (c, id, st) -> Spvc.spvc_compiler_has_decoration(c, id, Spv.SpvDecorationNonWritable));
        addStorageBindings(compiler, resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, layout, stack, CrossCompileUtil::isStorageBufferReadOnly);
    }

    private static void addBindingsForType(long compiler, long resources, int resourceType, StageBindingLayout layout, MemoryStack stack, ResourceClass cls, int space) {
        forEachResource(compiler, resources, resourceType, stack, (spvId, set, binding) -> {
            final SpvcHLSLResourceBinding b = SpvcHLSLResourceBinding.calloc(stack);
            Spvc.spvc_hlsl_resource_binding_init(b);
            b.stage(layout.execModel).desc_set(set).binding(binding);
            switch (cls) {
                case SRV_WITH_SAMPLER -> {
                    b.srv().set(space, binding);
                    b.sampler().set(space, binding);
                }
                case SAMPLER_ONLY -> b.sampler().set(space, binding);
                case CBV -> b.cbv().set(space, binding);
            }
            if (Spvc.spvc_compiler_hlsl_add_resource_binding(compiler, b) != Spvc.SPVC_SUCCESS) {
                LOG.warn("hlsl_add_resource_binding failed: stage=0x{} cls={} id={} set={} binding={}", Integer.toHexString(layout.execModel), cls, spvId, set, binding);
            }
        });
    }

    private static void addStorageBindings(long compiler, long resources, int resourceType, StageBindingLayout layout, MemoryStack stack, ReadOnlyClassifier classifier) {
        forEachResource(compiler, resources, resourceType, stack, (spvId, set, binding) -> {
            final boolean readOnly = classifier.isReadOnly(compiler, spvId, stack);
            final SpvcHLSLResourceBinding b = SpvcHLSLResourceBinding.calloc(stack);
            Spvc.spvc_hlsl_resource_binding_init(b);
            b.stage(layout.execModel).desc_set(set).binding(binding);
            if (readOnly || layout.uavSpace < 0) {
                b.srv().set(layout.srvSpace, binding);
            } else {
                b.uav().set(layout.uavSpace, binding);
            }
            if (Spvc.spvc_compiler_hlsl_add_resource_binding(compiler, b) != Spvc.SPVC_SUCCESS) {
                LOG.warn("hlsl_add_resource_binding (storage) failed: stage=0x{} type=0x{} id={} set={} binding={} ro={}", Integer.toHexString(layout.execModel), Integer.toHexString(resourceType), spvId, set, binding, readOnly);
            }
        });
    }

    @FunctionalInterface
    private interface ResourceVisitor { void accept(int spvId, int set, int binding); }

    private static void forEachResource(long compiler, long resources, int resourceType, MemoryStack stack, ResourceVisitor visitor) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) return;
        final int count = (int) pCount.get(0);
        if (count == 0) return;
        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        for (int i = 0; i < count; i++) {
            final int spvId = list.get(i).id();
            final int set     = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationDescriptorSet);
            final int binding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);
            visitor.accept(spvId, set, binding);
        }
    }
}
