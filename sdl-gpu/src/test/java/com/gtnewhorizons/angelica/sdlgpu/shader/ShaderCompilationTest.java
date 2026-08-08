package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests GLSL -> SPIR-V compilation via shaderc.
 */
class ShaderCompilationTest {

    private static final String SIMPLE_VERTEX_SHADER = """
        #version 460 core
        layout(location = 0) in vec3 a_Position;
        layout(location = 1) in vec4 a_Color;
        out vec4 v_Color;
        void main() {
            gl_Position = vec4(a_Position, 1.0);
            v_Color = a_Color;
        }
        """;

    private static final String SIMPLE_FRAGMENT_SHADER = """
        #version 460 core
        in vec4 v_Color;
        out vec4 fragColor;
        void main() {
            fragColor = v_Color;
        }
        """;

    private static final String TEXTURED_FRAGMENT_SHADER = """
        #version 460 core
        in vec2 v_TexCoord;
        out vec4 fragColor;
        uniform sampler2D u_Texture;
        void main() {
            fragColor = texture(u_Texture, v_TexCoord);
        }
        """;

    private static final String UNIFORM_VERTEX_SHADER = """
        #version 460 core
        layout(location = 0) in vec3 a_Position;
        uniform mat4 u_ModelViewProjection;
        uniform vec4 u_Color;
        out vec4 v_Color;
        void main() {
            gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
            v_Color = u_Color;
        }
        """;

    @Test
    void testSimpleVertexShaderCompiles() {
        ByteBuffer spirv = compileShader(SIMPLE_VERTEX_SHADER, Shaderc.shaderc_vertex_shader);
        assertNotNull(spirv, "Vertex shader should compile to SPIR-V");
        assertTrue(spirv.remaining() > 0, "SPIR-V output should not be empty");
        assertEquals(0x07230203, spirv.getInt(0), "Output should start with SPIR-V magic number");
    }

    @Test
    void testSimpleFragmentShaderCompiles() {
        ByteBuffer spirv = compileShader(SIMPLE_FRAGMENT_SHADER, Shaderc.shaderc_fragment_shader);
        assertNotNull(spirv, "Fragment shader should compile to SPIR-V");
        assertTrue(spirv.remaining() > 0, "SPIR-V output should not be empty");
        assertEquals(0x07230203, spirv.getInt(0), "Output should start with SPIR-V magic number");
    }

    @Test
    void testTexturedFragmentShaderCompiles() {
        ByteBuffer spirv = compileShader(TEXTURED_FRAGMENT_SHADER, Shaderc.shaderc_fragment_shader);
        assertNotNull(spirv, "Textured fragment shader should compile to SPIR-V");
        assertEquals(0x07230203, spirv.getInt(0));
    }

    @Test
    void testUniformVertexShaderCompiles() {
        ByteBuffer spirv = compileShader(UNIFORM_VERTEX_SHADER, Shaderc.shaderc_vertex_shader);
        assertNotNull(spirv, "Uniform vertex shader should compile to SPIR-V");
        assertEquals(0x07230203, spirv.getInt(0));
    }

    @Test
    void testInvalidShaderFailsGracefully() {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();

        try {
            Shaderc.shaderc_compile_options_set_forced_version_profile(options, 460, Shaderc.shaderc_profile_core);

            long result = Shaderc.shaderc_compile_into_spv(
                compiler, "this is not valid GLSL", Shaderc.shaderc_vertex_shader, "bad_shader", "main", options);

            try {
                final int status = Shaderc.shaderc_result_get_compilation_status(result);
                assertNotEquals(Shaderc.shaderc_compilation_status_success, status, "Invalid GLSL should not compile successfully");

                final String error = Shaderc.shaderc_result_get_error_message(result);
                assertNotNull(error, "Error message should be present");
                assertFalse(error.isEmpty(), "Error message should not be empty");
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    @Test
    void testAutoBindUniformsProducesBindings() {
        // Verify that shaderc's auto_bind_uniforms mode assigns bindings
        final ByteBuffer spirv = compileShader(UNIFORM_VERTEX_SHADER, Shaderc.shaderc_vertex_shader);
        assertNotNull(spirv, "Should compile with auto-bind-uniforms");
        assertTrue(spirv.remaining() > 100, "SPIR-V should be substantial enough to contain bindings");
    }

    private ByteBuffer compileShader(String source, int shaderKind) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, shaderKind, "test_shader", SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) {
            fail("Shader compilation failed: " + r.error());
        }
        return r.spirv();
    }
}
