package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.lwjgl.system.MemoryStack;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.*;

/**
 * Tests SPIRV-Cross reflection on compiled SPIR-V: Verifies we can extract uniform buffer names, sampler names, and vertex attribute locations.
 */
class SpvcReflectionTest {

    private static final String VERTEX_WITH_UNIFORMS = """
        #version 460 core
        layout(location = 0) in vec3 a_Position;
        layout(location = 1) in vec2 a_TexCoord;
        uniform mat4 u_ModelViewProjection;
        uniform vec4 u_Color;
        out vec2 v_TexCoord;
        out vec4 v_Color;
        void main() {
            gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
            v_TexCoord = a_TexCoord;
            v_Color = u_Color;
        }
        """;

    private static final String FRAGMENT_WITH_SAMPLER = """
        #version 460 core
        in vec2 v_TexCoord;
        in vec4 v_Color;
        out vec4 fragColor;
        uniform sampler2D u_Texture;
        uniform float u_Alpha;
        void main() {
            vec4 texColor = texture(u_Texture, v_TexCoord);
            fragColor = texColor * v_Color * vec4(1.0, 1.0, 1.0, u_Alpha);
        }
        """;

    @Test
    void testReflectUniformBuffersFromVertexShader() {
        ByteBuffer spirv = compileWithAutoBindUniforms(VERTEX_WITH_UNIFORMS, Shaderc.shaderc_vertex_shader);
        assertNotNull(spirv);

        List<String> uniformBuffers = reflectResourceNames(spirv, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER);
        assertFalse(uniformBuffers.isEmpty(), "Should find uniform buffers after auto-bind compilation");
    }

    @Test
    void testReflectSampledImagesFromFragmentShader() {
        ByteBuffer spirv = compileWithAutoBindUniforms(FRAGMENT_WITH_SAMPLER, Shaderc.shaderc_fragment_shader);
        assertNotNull(spirv);

        List<String> sampledImages = reflectResourceNames(spirv, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE);
        // With auto-combined-image-sampler, separate sampler+image become combined
        // Check both combined and separate paths
        List<String> separateImages = reflectResourceNames(spirv, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE);
        List<String> separateSamplers = reflectResourceNames(spirv, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_SAMPLERS);

        boolean foundTexture = sampledImages.stream().anyMatch(n -> n.contains("u_Texture"))
            || separateImages.stream().anyMatch(n -> n.contains("u_Texture"))
            || separateSamplers.stream().anyMatch(n -> n.contains("u_Texture"));

        assertTrue(foundTexture, "Should find u_Texture in sampled images, separate images, or separate samplers. "
            + "Combined: " + sampledImages + ", Images: " + separateImages + ", Samplers: " + separateSamplers);
    }

    @Test
    void testReflectProducesNonEmptyNames() {
        ByteBuffer spirv = compileWithAutoBindUniforms(VERTEX_WITH_UNIFORMS, Shaderc.shaderc_vertex_shader);
        assertNotNull(spirv);

        try (var stack = stackPush()) {
            long ctx = createSpvcContext(stack);
            long compiler = createGLSLCompiler(ctx, spirv, stack);
            long resources = createResources(compiler, stack);

            // Get uniform buffers
            PointerBuffer pList = stack.pointers(0);
            PointerBuffer pCount = stack.pointers(0);
            assertEquals(Spvc.SPVC_SUCCESS,
                Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, pList, pCount));

            long count = pCount.get(0);
            if (count > 0) {
                SpvcReflectedResource.Buffer resList = SpvcReflectedResource.create(pList.get(0), (int) count);
                for (int i = 0; i < count; i++) {
                    String name = resList.get(i).nameString();
                    assertNotNull(name, "Resource name should not be null");
                    assertFalse(name.isEmpty(), "Resource name should not be empty");
                }
            }

            Spvc.spvc_context_destroy(ctx);
        }
    }

    @Test
    void testSpvcContextCreationAndDestruction() {
        try (var stack = stackPush()) {
            PointerBuffer pCtx = stack.pointers(0);
            assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_context_create(pCtx));
            long ctx = pCtx.get(0);
            assertNotEquals(0, ctx, "Context should be non-null");
            Spvc.spvc_context_destroy(ctx);
        }
    }

    private List<String> reflectResourceNames(ByteBuffer spirv, int resourceType) {
        List<String> names = new ArrayList<>();
        try (var stack = stackPush()) {
            long ctx = createSpvcContext(stack);
            long compiler = createGLSLCompiler(ctx, spirv, stack);
            long resources = createResources(compiler, stack);

            PointerBuffer pList = stack.pointers(0);
            PointerBuffer pCount = stack.pointers(0);
            if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) == Spvc.SPVC_SUCCESS) {
                long count = pCount.get(0);
                if (count > 0) {
                    SpvcReflectedResource.Buffer resList = SpvcReflectedResource.create(pList.get(0), (int) count);
                    for (int i = 0; i < count; i++) {
                        String name = resList.get(i).nameString();
                        if (name != null && !name.isEmpty()) {
                            names.add(name);
                        }
                    }
                }
            }

            Spvc.spvc_context_destroy(ctx);
        }
        return names;
    }

    private long createSpvcContext(MemoryStack stack) {
        PointerBuffer pCtx = stack.pointers(0);
        assertEquals(Spvc.SPVC_SUCCESS, Spvc.spvc_context_create(pCtx));
        return pCtx.get(0);
    }

    private long createGLSLCompiler(long ctx, ByteBuffer spirv, MemoryStack stack) {
        IntBuffer spirvWords = spirv.asIntBuffer();
        PointerBuffer pIR = stack.pointers(0);
        assertEquals(Spvc.SPVC_SUCCESS,
            Spvc.spvc_context_parse_spirv(ctx, spirvWords, spirvWords.remaining(), pIR));

        PointerBuffer pCompiler = stack.pointers(0);
        assertEquals(Spvc.SPVC_SUCCESS,
            Spvc.spvc_context_create_compiler(ctx, Spvc.SPVC_BACKEND_GLSL, pIR.get(0),
                Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pCompiler));
        return pCompiler.get(0);
    }

    private long createResources(long compiler, MemoryStack stack) {
        PointerBuffer pResources = stack.pointers(0);
        assertEquals(Spvc.SPVC_SUCCESS,
            Spvc.spvc_compiler_create_shader_resources(compiler, pResources));
        return pResources.get(0);
    }

    private ByteBuffer compileWithAutoBindUniforms(String source, int shaderKind) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, shaderKind, "test", SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) {
            fail("Compilation failed: " + r.error());
        }
        return r.spirv();
    }
}
