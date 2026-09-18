package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryStack.stackPush;

@Tag("lwjgl3")
class FFPUniformBlockSpirvLayoutTest {

    @Test
    void vertexStageBlockMatchesJavaLayout() {
        final String glsl = "#version 460 core\n" + FFPUniformBlock.GLSL_DECL + "void main() { gl_Position = u_MVPMatrix * vec4(0.0, 0.0, 0.0, 1.0); }\n";
        assertBlockLayout(glsl, Shaderc.shaderc_vertex_shader);
    }

    @Test
    void fragmentStageBlockMatchesJavaLayout() {
        final FragmentKey fk = FragmentKey.fromPacked(new long[]{0L}, 1);
        assertBlockLayout(FragmentShaderGenerator.generate(fk), Shaderc.shaderc_fragment_shader);
    }

    static Stream<Arguments> instancedKeys() {
        return Stream.of(
            Arguments.of("cube-lightmap-texmat", cubeKey(true, true, false)),
            Arguments.of("cube-texgen", cubeKey(false, false, true)),
            Arguments.of("particle-fog-texmat", particleFogTexMatKey())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("instancedKeys")
    void instancedVariantCompilesToSpirv(String label, VertexKey key) {
        final String glsl = VertexShaderGenerator.generate(key);
        final SpirvCompiler.Result result = SpirvCompiler.compile(glsl, Shaderc.shaderc_vertex_shader, label, SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(result.spirv(), () -> "SPIR-V compile failed: " + result.error() + "\n" + glsl);
        MemoryUtil.memFree(result.spirv());
    }

    private static VertexKey cubeKey(boolean lightmapUnit, boolean unit0TextureMatrix, boolean texGen) {
        long bits = bit("BIT_HAS_VERTEX_COLOR") | bit("BIT_HAS_VERTEX_NORMAL") | bit("BIT_HAS_VERTEX_TEX") | bit("BIT_UNIT_TEX_BASE");
        if (lightmapUnit) bits |= 1L << (position("BIT_UNIT_TEX_BASE") + 1);
        if (unit0TextureMatrix || texGen) bits |= bit("BIT_UNIT_TEXMAT_BASE");
        if (texGen) {
            bits |= (long) VertexKey.TG_OBJ_LINEAR << position("BIT_TEXGEN_S");
            bits |= (long) VertexKey.TG_OBJ_LINEAR << position("BIT_TEXGEN_T");
        }
        return VertexKey.fromPacked(VertexKey.withInstancing(bits, Instancing.CUBE));
    }

    private static VertexKey particleFogTexMatKey() {
        final long bits = bit("BIT_UNIT_TEX_BASE") | 1L << (position("BIT_UNIT_TEX_BASE") + 1) | bit("BIT_UNIT_TEXMAT_BASE") | bit("BIT_FOG");
        return VertexKey.fromPacked(VertexKey.withInstancing(bits, Instancing.PARTICLE));
    }

    private static long bit(String name) {
        return 1L << position(name);
    }

    private static int position(String name) {
        return Reflect.getStatic(VertexKey.class, name);
    }

    private static void assertBlockLayout(String glsl, int shaderKind) {
        final SpirvCompiler.Result result = SpirvCompiler.compile(glsl, shaderKind, "ffp-layout-test", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(result.spirv(), () -> "SPIR-V compile failed: " + result.error() + "\n" + glsl);
        try {
            reflectAndCompare(result.spirv());
        } finally {
            MemoryUtil.memFree(result.spirv());
        }
    }

    private static void reflectAndCompare(ByteBuffer spirv) {
        try (var stack = stackPush()) {
            final PointerBuffer pCtx = stack.pointers(0);
            assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_context_create(pCtx));
            final long ctx = pCtx.get(0);
            try {
                final IntBuffer words = spirv.asIntBuffer();
                final PointerBuffer pIR = stack.pointers(0);
                assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_context_parse_spirv(ctx, words, words.remaining(), pIR));
                final PointerBuffer pCompiler = stack.pointers(0);
                assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_context_create_compiler(ctx, Spvc.SPVC_BACKEND_GLSL, pIR.get(0), Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pCompiler));
                final long compiler = pCompiler.get(0);

                final PointerBuffer pResources = stack.pointers(0);
                assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_compiler_create_shader_resources(compiler, pResources));
                final PointerBuffer pList = stack.pointers(0);
                final PointerBuffer pCount = stack.pointers(0);
                assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_resources_get_resource_list_for_type(pResources.get(0), Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, pList, pCount));
                assertEquals(1, (int) pCount.get(0), "expected exactly one UBO (the FFP block)");

                final SpvcReflectedResource ubo = SpvcReflectedResource.create(pList.get(0), 1).get(0);
                assertEquals(0, Spvc.spvc_compiler_get_decoration(compiler, ubo.id(), Spv.SpvDecorationBinding), "FFP block must auto-bind to binding 0 (SDL reflection keys on it)");

                final long typeHandle = Spvc.spvc_compiler_get_type_handle(compiler, ubo.base_type_id());
                final PointerBuffer pSize = stack.pointers(0);
                assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_compiler_get_declared_struct_size(compiler, typeHandle, pSize));
                assertEquals(FFPUniformBlock.SIZE, (int) pSize.get(0), "declared block size vs Java layout");

                final int memberCount = Spvc.spvc_type_get_num_member_types(typeHandle);
                assertEquals(FFPUniformBlock.MEMBER_OFFSETS.size(), memberCount, "member count vs Java layout");
                final IntBuffer pOffset = stack.ints(0);
                for (int i = 0; i < memberCount; i++) {
                    final String name = Spvc.spvc_compiler_get_member_name(compiler, ubo.base_type_id(), i);
                    assertNotNull(name);
                    assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_compiler_type_struct_member_offset(compiler, typeHandle, i, pOffset));
                    final String key = FFPUniformBlock.MEMBER_OFFSETS.containsKey(name) ? name : name + "[0]";
                    assertTrue(FFPUniformBlock.MEMBER_OFFSETS.containsKey(key), () -> "Java layout missing member " + name);
                    assertEquals(FFPUniformBlock.MEMBER_OFFSETS.getInt(key), pOffset.get(0), "offset of " + name);
                }
            } finally {
                Spvc.spvc_context_destroy(ctx);
            }
        }
    }
}
