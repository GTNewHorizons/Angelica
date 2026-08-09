package com.gtnewhorizons.angelica.sdlgpu.shader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ComputeSpirvRemapTest {

    private static final String COMPUTE_MIXED = """
        #version 460 core
        layout(local_size_x = 1) in;

        layout(std430, binding = 0) readonly buffer Visible { uint ids[]; } visible;
        layout(std430, binding = 1) buffer Cmds { uint data[]; } cmds;
        layout(std430, binding = 2) readonly buffer Meta { uint m[]; } meta;
        layout(std430, binding = 3) buffer Counter { uint count; } counter;

        layout(rgba8, binding = 0) readonly uniform image2D srcImage;
        layout(rgba8, binding = 1) writeonly uniform image2D dstImage;

        layout(std140, binding = 0) uniform Frustum { vec4 planes[6]; } frustum;

        void main() {
            // touch every binding so glslang doesn't optimize them away
            uint id = visible.ids[0];
            uint mm = meta.m[id];
            cmds.data[id] = mm;
            atomicAdd(counter.count, 1u);
            vec4 c = imageLoad(srcImage, ivec2(0));
            imageStore(dstImage, ivec2(0), c * frustum.planes[0]);
        }
        """;

    @Test
    void mixedRoRwLayout_setsAndBindings() {
        final ByteBuffer spirv = compile(COMPUTE_MIXED, Shaderc.shaderc_compute_shader);
        try {
            final ShaderManager.ComputeBindingMap map = ShaderManager.remapSpirvForComputeSDLGPU(spirv);

            // Two RO buffers, two RW
            assertEquals(2, map.roSsboGlSlots().length, "two RO SSBOs");
            assertEquals(2, map.rwSsboGlSlots().length, "two RW SSBOs");
            // One RO image, one RW image
            assertEquals(1, map.roStorageTextureGlSlots().length, "one RO storage image");
            assertEquals(1, map.rwStorageTextureGlSlots().length, "one RW storage image");
            // One UBO
            assertEquals(1, map.uboGlSlots().length, "one UBO");

            final ResourceLayout layout = captureResourceLayout(spirv);
            // 0 samplers + 1 RO image + 2 RO buffers
            assertEquals(3, layout.set0Bindings.size(), "set 0 should have 3 entries");
            assertEquals(Set.of(0, 1, 2), new TreeSet<>(layout.set0Bindings));
            // 1 RW image + 2 RW buffers
            assertEquals(3, layout.set1Bindings.size(), "set 1 should have 3 entries");
            assertEquals(Set.of(0, 1, 2), new TreeSet<>(layout.set1Bindings));
            // 1 UBO at binding 0
            assertEquals(List.of(0), new ArrayList<>(layout.set2Bindings));
            assertEquals(0, layout.otherSetCount, "no resources outside sets 0/1/2");

            assertEquals(2, layout.roSsbos.size(), "2 RO SSBOs in set 0"); for (int s : layout.roSsbos) assertEquals(0, s);
            assertEquals(2, layout.rwSsbos.size(), "2 RW SSBOs in set 1"); for (int s : layout.rwSsbos) assertEquals(1, s);
            assertEquals(1, layout.roImages.size(), "1 RO image in set 0"); for (int s : layout.roImages) assertEquals(0, s);
            assertEquals(1, layout.rwImages.size(), "1 RW image in set 1"); for (int s : layout.rwImages) assertEquals(1, s);
            assertEquals(1, layout.ubos.size(),     "1 UBO in set 2");      for (int s : layout.ubos) assertEquals(2, s);

            assertContains(map.roSsboGlSlots(), 0); // visible
            assertContains(map.roSsboGlSlots(), 2); // meta
            assertContains(map.rwSsboGlSlots(), 1); // cmds
            assertContains(map.rwSsboGlSlots(), 3); // counter
            assertContains(map.roStorageTextureGlSlots(), 0);
            assertContains(map.rwStorageTextureGlSlots(), 1);
            assertContains(map.uboGlSlots(), 0);

            assertEquals(1, map.uboIsDefaultBlock().length);
            assertFalse(map.uboIsDefaultBlock()[0]);
            assertEquals(96, map.uboSizes()[0]);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static final class ResourceLayout {
        final List<Integer> set0Bindings = new ArrayList<>();
        final List<Integer> set1Bindings = new ArrayList<>();
        final List<Integer> set2Bindings = new ArrayList<>();
        int otherSetCount;
        final List<Integer> roSsbos = new ArrayList<>();
        final List<Integer> rwSsbos = new ArrayList<>();
        final List<Integer> roImages = new ArrayList<>();
        final List<Integer> rwImages = new ArrayList<>();
        final List<Integer> ubos = new ArrayList<>();
    }

    private static ResourceLayout captureResourceLayout(ByteBuffer spirv) {
        final ResourceLayout out = new ResourceLayout();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer pCtx = stack.pointers(0);
            if (Spvc.spvc_context_create(pCtx) != Spvc.SPVC_SUCCESS) fail("spvc_context_create");
            final long ctx = pCtx.get(0);
            try {
                final IntBuffer words = spirv.asIntBuffer();
                final PointerBuffer pIR = stack.pointers(0);
                if (Spvc.spvc_context_parse_spirv(ctx, words, words.remaining(), pIR) != Spvc.SPVC_SUCCESS) fail("parse_spirv");
                final PointerBuffer pComp = stack.pointers(0);
                if (Spvc.spvc_context_create_compiler(ctx, Spvc.SPVC_BACKEND_GLSL, pIR.get(0),
                        Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pComp) != Spvc.SPVC_SUCCESS) fail("create_compiler");
                final long comp = pComp.get(0);
                final PointerBuffer pRes = stack.pointers(0);
                if (Spvc.spvc_compiler_create_shader_resources(comp, pRes) != Spvc.SPVC_SUCCESS) fail("create_shader_resources");
                final long res = pRes.get(0);

                walkType(res, comp, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, (id, set, bind) -> {
                    recordSet(out, set, bind);
                    final boolean ro = isReadOnlyBlock(comp, id);
                    (ro ? out.roSsbos : out.rwSsbos).add(set);
                });
                walkType(res, comp, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE, (id, set, bind) -> {
                    recordSet(out, set, bind);
                    final boolean ro = Spvc.spvc_compiler_has_decoration(comp, id, Spv.SpvDecorationNonWritable);
                    (ro ? out.roImages : out.rwImages).add(set);
                });
                walkType(res, comp, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, (id, set, bind) -> {
                    recordSet(out, set, bind);
                    out.ubos.add(set);
                });
            } finally {
                Spvc.spvc_context_destroy(ctx);
            }
        }
        return out;
    }

    private static void recordSet(ResourceLayout out, int set, int bind) {
        switch (set) {
            case 0 -> out.set0Bindings.add(bind);
            case 1 -> out.set1Bindings.add(bind);
            case 2 -> out.set2Bindings.add(bind);
            default -> out.otherSetCount++;
        }
    }

    private static boolean isReadOnlyBlock(long compiler, int varId) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer pDecorations = stack.pointers(0);
            final PointerBuffer pNum = stack.pointers(0);
            if (Spvc.spvc_compiler_get_buffer_block_decorations(compiler, varId, pDecorations, pNum) != Spvc.SPVC_SUCCESS) return false;
            final long n = pNum.get(0);
            if (n <= 0) return false;
            final IntBuffer decs = MemoryUtil.memIntBuffer(pDecorations.get(0), (int) n);
            for (int i = 0; i < n; i++) if (decs.get(i) == Spv.SpvDecorationNonWritable) return true;
            return false;
        }
    }

    @FunctionalInterface
    private interface ResourceVisitor { void visit(int id, int set, int binding); }

    private static void walkType(long resources, long compiler, int resourceType, ResourceVisitor v) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer pList = stack.pointers(0);
            final PointerBuffer pCount = stack.pointers(0);
            if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) return;
            final int n = (int) pCount.get(0);
            if (n == 0) return;
            final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), n);
            for (int i = 0; i < n; i++) {
                final SpvcReflectedResource r = list.get(i);
                final int set = Spvc.spvc_compiler_get_decoration(compiler, r.id(), Spv.SpvDecorationDescriptorSet);
                final int bind = Spvc.spvc_compiler_get_decoration(compiler, r.id(), Spv.SpvDecorationBinding);
                v.visit(r.id(), set, bind);
            }
        }
    }

    private static final String COMPUTE_LOOSE_UNIFORMS = """
        #version 460 core
        layout(local_size_x = 1) in;

        uniform float frameTime;
        uniform vec4 tint;

        layout(rgba8, binding = 0) writeonly uniform image2D dst;

        void main() {
            imageStore(dst, ivec2(0), tint * frameTime);
        }
        """;

    @Test
    void looseUniforms_flaggedAsDefaultBlock() {
        final ByteBuffer spirv = compile(COMPUTE_LOOSE_UNIFORMS, Shaderc.shaderc_compute_shader);
        try {
            final ShaderManager.ComputeBindingMap map = ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            assertEquals(1, map.uboGlSlots().length, "one synthetic UBO");
            assertTrue(map.uboIsDefaultBlock()[0], "loose-uniform block must be flagged as default");
            assertTrue(map.uboSizes()[0] >= 20, "declared size covers float + vec4");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static final String COMPUTE_NO_RESOURCES = """
        #version 460 core
        layout(local_size_x = 1) in;
        void main() {}
        """;

    @Test
    void noResources_emptyBindingMap() {
        final ByteBuffer spirv = compile(COMPUTE_NO_RESOURCES, Shaderc.shaderc_compute_shader);
        try {
            final ShaderManager.ComputeBindingMap map = ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            assertEquals(0, map.roSsboGlSlots().length);
            assertEquals(0, map.rwSsboGlSlots().length);
            assertEquals(0, map.roStorageTextureGlSlots().length);
            assertEquals(0, map.rwStorageTextureGlSlots().length);
            assertEquals(0, map.uboGlSlots().length);
            assertEquals(0, map.samplerGlSlots().length);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void mixedRoRw_reflectionSplitCounts() {
        final ByteBuffer spirv = compile(COMPUTE_MIXED, Shaderc.shaderc_compute_shader);
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            assertEquals(2, refl.numReadonlyStorageBuffers());
            assertEquals(2, refl.numReadwriteStorageBuffers());
            assertEquals(1, refl.numReadonlyStorageTextures());
            assertEquals(1, refl.numReadwriteStorageTextures());
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static void assertContains(int[] arr, int value) {
        for (int v : arr) if (v == value) return;
        fail("expected " + value + " in " + Arrays.toString(arr));
    }

    private static ByteBuffer compile(String source, int shaderKind) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, shaderKind, "compute_test", SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) {
            fail("compute compile failed: " + r.error());
        }
        return r.spirv();
    }
}
