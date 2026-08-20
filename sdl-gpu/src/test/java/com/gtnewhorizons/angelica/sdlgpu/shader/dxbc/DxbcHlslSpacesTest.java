package com.gtnewhorizons.angelica.sdlgpu.shader.dxbc;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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

    @Test
    void vsSpaceMapping_ubosAtSpace1_samplersAtSpace0() {
        final ByteBuffer spirv = compile(VS_WITH_UBO_AND_SAMPLER, Shaderc.shaderc_vertex_shader);
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_VERTEX_SHADER);
            final String hlsl = DxbcCrossCompile.compileToHlsl(spirv, GL20.GL_VERTEX_SHADER);

            assertTrue(hlsl.contains("space1"), "VS HLSL must reference space1 for the UBO. HLSL:\n" + hlsl);
            assertFalse(hlsl.contains("space2"), "VS HLSL must not reference space2. HLSL:\n" + hlsl);
            assertFalse(hlsl.contains("space3"), "VS HLSL must not reference space3. HLSL:\n" + hlsl);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void fsSpaceMapping_ubosAtSpace3_samplersAtSpace2() {
        final ByteBuffer spirv = compile(FS_WITH_UBO_AND_SAMPLER, Shaderc.shaderc_fragment_shader);
        try {
            ShaderManager.remapSpirvForSDLGPU(spirv, GL20.GL_FRAGMENT_SHADER);
            final String hlsl = DxbcCrossCompile.compileToHlsl(spirv, GL20.GL_FRAGMENT_SHADER);

            assertTrue(hlsl.contains("space2"), "FS HLSL must reference space2 for the sampler/SRV. HLSL:\n" + hlsl);
            assertTrue(hlsl.contains("space3"), "FS HLSL must reference space3 for the UBO. HLSL:\n" + hlsl);
            assertFalse(hlsl.contains("space1"), "FS HLSL must not reference space1. HLSL:\n" + hlsl);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }


    @Test
    void ldsMultiMipCompute_hlslHasGroupsharedAndBarrier() {
        final ByteBuffer spirv = compile(SpirvTestShaders.LDS_MULTI_MIP_GLSL, Shaderc.shaderc_compute_shader);
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(spirv);
            final String hlsl = DxbcCrossCompile.compileToHlsl(spirv, GL43.GL_COMPUTE_SHADER);
            assertTrue(hlsl.contains("groupshared"), "shared array must become groupshared. HLSL:\n" + hlsl);
            assertTrue(hlsl.contains("GroupMemoryBarrierWithGroupSync"), "barrier() must become GroupMemoryBarrierWithGroupSync. HLSL:\n" + hlsl);
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static ByteBuffer compile(String source, int shaderKind) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, shaderKind, "hlsl_spaces_test",
            SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) {
            fail("shader compile failed: " + r.error());
        }
        return r.spirv();
    }
}
