package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdlSamplerStripperPreprocessorTest {

    static Stream<Arguments> referenced() {
        return Stream.of(
            Arguments.of("samplerReferencedOnlyInsideIfBlock_isPreserved", "uniform sampler2D u_S",
                "#version 460 core\n"
                    + "#define FOO 1\n"
                    + "layout(binding = 0) uniform sampler2D u_S;\n"
                    + "void main() {\n"
                    + "#if FOO\n"
                    + "    float a = textureLod(u_S, vec2(0), 0.0).r;\n"
                    + "#endif\n"
                    + "}\n"),
            Arguments.of("samplerReferencedUnconditionally_isPreserved", "uniform sampler2D u_S",
                "#version 460 core\n"
                    + "#define FOO 1\n"
                    + "layout(binding = 0) uniform sampler2D u_S;\n"
                    + "void main() {\n"
                    + "    float t = textureLod(u_S, vec2(0), 0.0).r;\n"
                    + "#if FOO\n"
                    + "    float a = t * 2.0;\n"
                    + "#endif\n"
                    + "}\n"),
            Arguments.of("samplerReferencedInBothIfAndElseBranches_isPreserved", "uniform sampler2D u_S",
                "#version 460 core\n"
                    + "#define FOO 0\n"
                    + "layout(binding = 0) uniform sampler2D u_S;\n"
                    + "void main() {\n"
                    + "#if FOO\n"
                    + "    float a = textureLod(u_S, vec2(0), 0.0).r;\n"
                    + "#else\n"
                    + "    float b = textureLod(u_S, vec2(0), 0.0).r;\n"
                    + "#endif\n"
                    + "}\n"),
            Arguments.of("terrainOpaqueLayout_keepsBlockTex", "uniform sampler2D u_BlockTex",
                "#version 330 core\n"
                    + "#define USE_RGSS\n"
                    + "in vec2 v_TexCoord;\n"
                    + "out vec4 fragColor;\n"
                    + "uniform sampler2D u_BlockTex;\n"
                    + "#ifdef USE_RGSS\n"
                    + "vec4 sampleRGSS(sampler2D tex, vec2 uv) { return textureLod(tex, uv, 0.0); }\n"
                    + "#endif\n"
                    + "void main() {\n"
                    + "#ifdef USE_RGSS\n"
                    + "    vec2 texSize = vec2(textureSize(u_BlockTex, 0));\n"
                    + "    fragColor = sampleRGSS(u_BlockTex, v_TexCoord);\n"
                    + "#else\n"
                    + "    fragColor = texture(u_BlockTex, v_TexCoord);\n"
                    + "#endif\n"
                    + "}\n"),
            Arguments.of("samplerOnlyInMacroBody_isPreserved", "uniform sampler2D u_S",
                "#version 460 core\n"
                    + "layout(binding = 0) uniform sampler2D u_S;\n"
                    + "#define SAMPLE_IT() textureLod(u_S, vec2(0), 0.0).r\n"
                    + "void main() {\n"
                    + "    float a = SAMPLE_IT();\n"
                    + "}\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("referenced")
    void referencedSamplerIsPreserved(String name, String declaration, String src) {
        final String out = ShaderTransformChain.stripUnused(src);
        assertTrue(out.contains(declaration), name + ": the declaration must survive; output:\n" + out);
    }

    static Stream<Arguments> unreferenced() {
        return Stream.of(
            Arguments.of("samplerNeverReferenced_isStripped", "uniform sampler2D iris_centerDepthSmooth",
                "#version 460 core\n"
                    + "#define FOO 1\n"
                    + "layout(binding = 0) uniform sampler2D iris_centerDepthSmooth;\n"
                    + "void main() {\n"
                    + "#if FOO\n"
                    + "    float a = 1.0;\n"
                    + "#endif\n"
                    + "}\n"),
            Arguments.of("samplerOnlyInCommentInsidePreprocessorBlock_isStripped", "uniform sampler2D u_S",
                "#version 460 core\n"
                    + "#define FOO 1\n"
                    + "layout(binding = 0) uniform sampler2D u_S;\n"
                    + "void main() {\n"
                    + "#if FOO\n"
                    + "    // u_S was used here once\n"
                    + "    float a = 1.0;\n"
                    + "#endif\n"
                    + "}\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreferenced")
    void unreferencedSamplerIsStripped(String name, String declaration, String src) {
        final String out = ShaderTransformChain.stripUnused(src);
        assertFalse(out.contains(declaration), name + ": the declaration must be stripped; output:\n" + out);
    }

    @Test
    void sourceWithoutSamplersReturnsTheSourceInstance() {
        final String src = "#version 460 core\n"
            + "in vec3 v_Position;\n"
            + "void main() {\n"
            + "    gl_Position = vec4(v_Position, 1.0);\n"
            + "}\n";
        assertSame(src, ShaderTransformChain.stripUnused(src));
    }
}
