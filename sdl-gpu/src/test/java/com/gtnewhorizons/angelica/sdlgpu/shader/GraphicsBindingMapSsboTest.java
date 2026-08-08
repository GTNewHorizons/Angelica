package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class GraphicsBindingMapSsboTest {

    private static final String FRAG_WITH_SSBO = """
        #version 460 core
        layout(std430, binding = 5) readonly buffer Block { uint data[]; } blk;
        layout(set = 0, binding = 0) uniform sampler2D s0;
        layout(location = 0) in vec2 uv;
        layout(location = 0) out vec4 o;
        void main() {
            uint x = blk.data[0];
            o = texture(s0, uv) + vec4(float(x));
        }
        """;

    @Test
    void extractsOriginalSsboBinding() {
        final ByteBuffer spirv = compile(FRAG_WITH_SSBO, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.GraphicsBindingMap map = ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_FRAGMENT_SHADER);
            assertEquals(1, map.roSsboGlSlots().length);
            assertEquals(5, map.roSsboGlSlots()[0], "pre-remap binding decoration preserved");

            final int[] setBindingForSsbo = findFirstResource(spirv, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER);
            assertEquals(2, setBindingForSsbo[0], "fragment resource set");
            assertEquals(1, setBindingForSsbo[1], "binding 1 (after the one sampler at 0)");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void emptyMapForShaderWithoutSsbo() {
        final String src = """
            #version 460 core
            layout(set = 0, binding = 0) uniform sampler2D s0;
            layout(location = 0) in vec2 uv;
            layout(location = 0) out vec4 o;
            void main() { o = texture(s0, uv); }
            """;
        final ByteBuffer spirv = compile(src, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.GraphicsBindingMap map = ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_FRAGMENT_SHADER);
            assertEquals(0, map.roSsboGlSlots().length);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static int[] findFirstResource(ByteBuffer spirv, int resourceType) {
        final int[] out = new int[] { -1, -1 };
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer pCtx = stack.pointers(0);
            if (Spvc.spvc_context_create(pCtx) != Spvc.SPVC_SUCCESS) fail("spvc_context_create");
            final long ctx = pCtx.get(0);
            try {
                final IntBuffer words = spirv.asIntBuffer();
                final PointerBuffer pIR = stack.pointers(0);
                if (Spvc.spvc_context_parse_spirv(ctx, words, words.remaining(), pIR) != Spvc.SPVC_SUCCESS) fail("parse_spirv");
                final PointerBuffer pComp = stack.pointers(0);
                if (Spvc.spvc_context_create_compiler(ctx, Spvc.SPVC_BACKEND_GLSL, pIR.get(0), Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pComp) != Spvc.SPVC_SUCCESS) fail("create_compiler");
                final long comp = pComp.get(0);
                final PointerBuffer pRes = stack.pointers(0);
                if (Spvc.spvc_compiler_create_shader_resources(comp, pRes) != Spvc.SPVC_SUCCESS) fail("create_shader_resources");
                final long res = pRes.get(0);

                final PointerBuffer pList = stack.pointers(0);
                final PointerBuffer pCount = stack.pointers(0);
                if (Spvc.spvc_resources_get_resource_list_for_type(res, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) return out;
                final int n = (int) pCount.get(0);
                if (n == 0) return out;
                final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), n);
                final SpvcReflectedResource r = list.get(0);
                out[0] = Spvc.spvc_compiler_get_decoration(comp, r.id(), Spv.SpvDecorationDescriptorSet);
                out[1] = Spvc.spvc_compiler_get_decoration(comp, r.id(), Spv.SpvDecorationBinding);
            } finally {
                Spvc.spvc_context_destroy(ctx);
            }
        }
        return out;
    }

    private static ByteBuffer compile(String source, int shaderKind) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, shaderKind, "frag_test", SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) {
            fail("frag compile failed: " + r.error());
        }
        return r.spirv();
    }
}
