package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdlSamplerStripperPreprocessorTest {

    @Test
    void samplerReferencedOnlyInsideIfBlock_isPreserved() {
        final String src = "#version 460 core\n"
            + "#define FOO 1\n"
            + "layout(binding = 0) uniform sampler2D u_S;\n"
            + "void main() {\n"
            + "#if FOO\n"
            + "    float a = textureLod(u_S, vec2(0), 0.0).r;\n"
            + "#endif\n"
            + "}\n";
        final String out = SamplerStripper.stripUnused(src);
        assertTrue(out.contains("uniform sampler2D u_S"), "ref inside #if must keep the decl; output:\n" + out);
    }

    @Test
    void samplerReferencedUnconditionally_isPreserved() {
        final String src = "#version 460 core\n"
            + "#define FOO 1\n"
            + "layout(binding = 0) uniform sampler2D u_S;\n"
            + "void main() {\n"
            + "    float t = textureLod(u_S, vec2(0), 0.0).r;\n"
            + "#if FOO\n"
            + "    float a = t * 2.0;\n"
            + "#endif\n"
            + "}\n";
        final String out = SamplerStripper.stripUnused(src);
        assertTrue(out.contains("uniform sampler2D u_S"),
            "unconditional ref must keep the decl; output:\n" + out);
    }

    @Test
    void samplerReferencedInBothIfAndElseBranches_isPreserved() {
        final String src = "#version 460 core\n"
            + "#define FOO 0\n"
            + "layout(binding = 0) uniform sampler2D u_S;\n"
            + "void main() {\n"
            + "#if FOO\n"
            + "    float a = textureLod(u_S, vec2(0), 0.0).r;\n"
            + "#else\n"
            + "    float b = textureLod(u_S, vec2(0), 0.0).r;\n"
            + "#endif\n"
            + "}\n";
        final String out = SamplerStripper.stripUnused(src);
        assertTrue(out.contains("uniform sampler2D u_S"), "refs in both branches must keep the decl; output:\n" + out);
    }

    @Test
    void terrainOpaqueLayout_keepsBlockTex() {
        final String src = "#version 330 core\n"
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
            + "}\n";
        final String out = SamplerStripper.stripUnused(src);
        assertTrue(out.contains("uniform sampler2D u_BlockTex"), "terrain sampler must survive; output:\n" + out);
    }

    @Test
    void samplerNeverReferenced_isStripped() {
        final String src = "#version 460 core\n"
            + "#define FOO 1\n"
            + "layout(binding = 0) uniform sampler2D iris_centerDepthSmooth;\n"
            + "void main() {\n"
            + "#if FOO\n"
            + "    float a = 1.0;\n"
            + "#endif\n"
            + "}\n";
        final String out = SamplerStripper.stripUnused(src);
        assertFalse(out.contains("uniform sampler2D iris_centerDepthSmooth"), "an unreferenced sampler must still be stripped; output:\n" + out);
    }

    @Test
    void samplerOnlyInCommentInsidePreprocessorBlock_isStripped() {
        final String src = "#version 460 core\n"
            + "#define FOO 1\n"
            + "layout(binding = 0) uniform sampler2D u_S;\n"
            + "void main() {\n"
            + "#if FOO\n"
            + "    // u_S was used here once\n"
            + "    float a = 1.0;\n"
            + "#endif\n"
            + "}\n";
        final String out = SamplerStripper.stripUnused(src);
        assertFalse(out.contains("uniform sampler2D u_S"), "a comment mention must not keep the decl; output:\n" + out);
    }

    @Test
    void samplerOnlyInMacroBody_isPreserved() {
        final String src = "#version 460 core\n"
            + "layout(binding = 0) uniform sampler2D u_S;\n"
            + "#define SAMPLE_IT() textureLod(u_S, vec2(0), 0.0).r\n"
            + "void main() {\n"
            + "    float a = SAMPLE_IT();\n"
            + "}\n";
        final String out = SamplerStripper.stripUnused(src);
        assertTrue(out.contains("uniform sampler2D u_S"), "a macro-body ref must keep the decl; output:\n" + out);
    }
}
