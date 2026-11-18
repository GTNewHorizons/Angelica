package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.List;

import static com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders.compileOrFail;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UnusedSamplerReflectionTest {

    private static final String FRAGMENT = """
        #version 330 core
        uniform sampler2D tex;
        uniform sampler2D normals;
        uniform sampler2D specular;
        in vec2 uv;
        out vec4 fragColor;
        float highlight(float d) {
            float specular = d * d;
            return specular;
        }
        void main() {
            fragColor = texture(tex, uv) * highlight(uv.x);
        }
        """;

    @Test
    void samplersOnlyShadowedOrDeclaredAreUnused() {
        final ByteBuffer spirv = compileOrFail(FRAGMENT, Shaderc.shaderc_fragment_shader, SpirvCompiler.Options.vulkanRelaxed());
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            assertEquals(List.of("normals", "specular"), refl.unusedSamplerNames().stream().sorted().toList(),
                "declared samplers: " + refl.samplerNames());
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }
}
