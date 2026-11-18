package com.gtnewhorizons.angelica.sdlgpu.shader.dxbc;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders.compileOrFail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DxbcHlslSpacesTest {

    private static final String VS_WITH_UBO_AND_SAMPLER = """
        #version 460 core
        layout(location = 0) in vec3 a_Position;
        layout(location = 2) in vec2 a_TexCoord0;
        layout(std140, binding = 0) uniform Matrices { mat4 mvp; } u;
        layout(binding = 0) uniform sampler2D vsSampler;
        layout(location = 0) out vec2 v_TexCoord0;
        void main() {
            // touch vsSampler so glslang doesn't strip it
            float bias = texture(vsSampler, vec2(0.0)).r;
            gl_Position = u.mvp * vec4(a_Position, 1.0 + bias * 0.0);
            v_TexCoord0 = a_TexCoord0;
        }
        """;

    private static final String FS_WITH_UBO_AND_SAMPLER = """
        #version 460 core
        layout(location = 0) in vec2 v_TexCoord0;
        layout(binding = 0) uniform sampler2D u_Sampler0;
        layout(std140, binding = 0) uniform Color { vec4 tint; } c;
        layout(location = 0) out vec4 fragColor;
        void main() {
            fragColor = texture(u_Sampler0, v_TexCoord0) * c.tint;
        }
        """;

    static Stream<Arguments> stages() {
        return Stream.of(
            Arguments.of("VS: UBO at space1", VS_WITH_UBO_AND_SAMPLER, Shaderc.shaderc_vertex_shader, GL20.GL_VERTEX_SHADER,
                List.of("space1"), List.of("space2", "space3")),
            Arguments.of("FS: sampler at space2, UBO at space3", FS_WITH_UBO_AND_SAMPLER, Shaderc.shaderc_fragment_shader, GL20.GL_FRAGMENT_SHADER,
                List.of("space2", "space3"), List.of("space1")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stages")
    void stageUsesOnlyItsOwnRegisterSpaces(String name, String glsl, int shadercKind, int glStage, List<String> present, List<String> absent) {
        final ByteBuffer spirv = compileOrFail(glsl, shadercKind);
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, glStage);
            final String hlsl = DxbcCrossCompile.compileToHlsl(spirv, glStage);
            for (String space : present) assertTrue(hlsl.contains(space), name + ": HLSL must reference " + space + ". HLSL:\n" + hlsl);
            for (String space : absent) assertFalse(hlsl.contains(space), name + ": HLSL must not reference " + space + ". HLSL:\n" + hlsl);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void ldsMultiMipCompute_hlslHasGroupsharedAndBarrier() {
        final ByteBuffer spirv = compileOrFail(SpirvTestShaders.LDS_MULTI_MIP_GLSL, Shaderc.shaderc_compute_shader);
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            final String hlsl = DxbcCrossCompile.compileToHlsl(spirv, GL43.GL_COMPUTE_SHADER);
            assertTrue(hlsl.contains("groupshared"), "shared array must become groupshared. HLSL:\n" + hlsl);
            assertTrue(hlsl.contains("GroupMemoryBarrierWithGroupSync"), "barrier() must become GroupMemoryBarrierWithGroupSync. HLSL:\n" + hlsl);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }
}
