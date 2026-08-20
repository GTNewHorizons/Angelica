package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrewarmSingleParseEquivalenceTest {

    @AfterEach
    void clearBlocks() {
        GLSMHooks.perFrameUniformBlock = null;
        GLSMHooks.perPassUniformBlock = null;
    }

    private static String legacy(String source, int glShaderType) {
        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(source, glShaderType, "test", true);
        String src = pre != null ? pre.rewrittenSource() : source;
        if (glShaderType == GL20.GL_VERTEX_SHADER) {
            src = ClipZRemap.injectGLToVulkanClipZ(src);
        }
        src = SamplerStripper.stripUnused(src);
        if (glShaderType == GL20.GL_VERTEX_SHADER || glShaderType == GL20.GL_FRAGMENT_SHADER) {
            src = PerFrameBlockInjector.inject(src, GLSMHooks.perFrameUniformBlock, GLSMHooks.perPassUniformBlock);
        }
        return src;
    }

    private static void assertEquivalent(String label, String source, int glShaderType) {
        GlslVulkanPreprocess.clearCache();
        final String legacyOut = legacy(source, glShaderType);
        GlslVulkanPreprocess.clearCache();
        final String parseOnceOut = ShaderManager.applyPrewarmTransforms(source, glShaderType);
        assertEquals(legacyOut, parseOnceOut, "single-parse vs chained pipeline diverged for " + label);
    }

    private static void assertEquivalentIgnoringWhitespace(String label, String source, int glShaderType) {
        GlslVulkanPreprocess.clearCache();
        final String legacyOut = legacy(source, glShaderType);
        GlslVulkanPreprocess.clearCache();
        final String parseOnceOut = ShaderManager.applyPrewarmTransforms(source, glShaderType);
        assertEquals(legacyOut.replaceAll("\\s+", " "), parseOnceOut.replaceAll("\\s+", " "), "single-parse vs chained pipeline diverged beyond whitespace for " + label);
    }

    @Test
    void minimalFragment_noTransformsNeeded() {
        final String src = "#version 460 core\n"
            + "layout(location = 0) out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(1.0); }\n";
        assertEquivalent("minimal-fragment", src, GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void minimalVertex_depthRemapInjected() {
        final String src = "#version 460 core\n"
            + "layout(location = 0) in vec3 a_Position;\n"
            + "void main() { gl_Position = vec4(a_Position, 1.0); }\n";
        assertEquivalent("minimal-vertex", src, GL20.GL_VERTEX_SHADER);
    }

    @Test
    void fragmentWithUnusedSampler_strippedAndIdsRewritten() {
        final String src = "#version 460 core\n"
            + "uniform sampler2D u_Unused;\n"
            + "uniform sampler2D u_Used;\n"
            + "layout(location = 0) out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = texture(u_Used, vec2(0.5));\n"
            + "}\n";
        assertEquivalent("frag-unused-sampler", src, GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void vertexWithUnlocatedInputs_layoutLocationsInserted() {
        final String src = "#version 460 core\n"
            + "layout(location = 0) in vec3 a_Position;\n"
            + "in vec2 a_TexCoord;\n"
            + "in vec3 a_Normal;\n"
            + "out vec2 v_TexCoord;\n"
            + "void main() {\n"
            + "    v_TexCoord = a_TexCoord;\n"
            + "    gl_Position = vec4(a_Position + a_Normal, 1.0);\n"
            + "}\n";
        assertEquivalent("vertex-unlocated-inputs", src, GL20.GL_VERTEX_SHADER);
    }

    @Test
    void vertexWithBuiltinIdRewrites() {
        final String src = "#version 460 core\n"
            + "void main() {\n"
            + "    int v = gl_VertexID;\n"
            + "    int i = gl_InstanceID;\n"
            + "    gl_Position = vec4(float(v + i), 0.0, 0.0, 1.0);\n"
            + "}\n";
        assertEquivalent("vertex-builtin-ids", src, GL20.GL_VERTEX_SHADER);
    }

    @Test
    void fragmentWithBoolUniformsAndSamplerRename() {
        final String src = "#version 460 core\n"
            + "uniform bool u_Enabled;\n"
            + "uniform float u_Gain = 1.5;\n"
            + "layout(location = 0) out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = vec4(u_Enabled ? u_Gain : 0.0);\n"
            + "}\n";
        assertEquivalent("frag-bool-uniform-init", src, GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void samplerOnlyReferencedInsidePreprocessorBlock_preservedConsistently() {
        final String src = "#version 460 core\n"
            + "#define FOO 1\n"
            + "layout(binding = 0) uniform sampler2D u_S;\n"
            + "void main() {\n"
            + "#if FOO\n"
            + "    float a = textureLod(u_S, vec2(0), 0.0).r;\n"
            + "#endif\n"
            + "}\n";
        assertEquivalent("sampler-in-preprocessor-block", src, GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void compute_noVertexOnlyPasses() {
        final String src = "#version 460 core\n"
            + "layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;\n"
            + "uniform sampler2D u_Unused;\n"
            + "void main() {}\n";
        assertEquivalent("compute-empty", src, GL43.GL_COMPUTE_SHADER);
    }

    @Test
    void geometry_noVertexOnlyPasses() {
        final String src = "#version 460 core\n"
            + "layout(triangles) in;\n"
            + "layout(triangle_strip, max_vertices = 3) out;\n"
            + "void main() {\n"
            + "    for (int i = 0; i < 3; i++) {\n"
            + "        gl_Position = gl_in[i].gl_Position;\n"
            + "        EmitVertex();\n"
            + "    }\n"
            + "    EndPrimitive();\n"
            + "}\n";
        assertEquivalent("geometry-passthrough", src, GL32.GL_GEOMETRY_SHADER);
    }

    @Test
    void unparseable_fallsBackToInputUnchanged() {
        final String src = "this is not valid GLSL @@@@\n";
        assertEquivalent("unparseable", src, GL20.GL_FRAGMENT_SHADER);
    }

    private static final PerFrameUniformBlock PER_FRAME = new PerFrameUniformBlock(List.of(
        new Member("cameraPosition", UniformType.VEC3),
        new Member("frameTimeCounter", UniformType.FLOAT)));

    private static final PerFrameUniformBlock PER_PASS = new PerFrameUniformBlock(List.of(
        new Member("alphaTestRef", UniformType.FLOAT)));

    @Test
    void perFrameBlock_replaceableUniformStrippedAndInjected() {
        GLSMHooks.perFrameUniformBlock = PER_FRAME;
        GLSMHooks.perPassUniformBlock = PER_PASS;
        final String src = "#version 460 core\n"
            + "uniform float frameTimeCounter;\n"
            + "uniform float alphaTestRef;\n"
            + "layout(location = 0) out vec4 fragColor;\n"
            + "void main() {\n"
            + "    if (fragColor.a < alphaTestRef) discard;\n"
            + "    fragColor = vec4(frameTimeCounter);\n"
            + "}\n";
        assertEquivalent("pfb-replaceable", src, GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void perFrameBlock_firstDeclIsUnlocatedVsInput() {
        GLSMHooks.perFrameUniformBlock = PER_FRAME;
        final String src = "#version 460 core\n"
            + "in vec3 a_Position;\n"
            + "layout(location = 5) in vec2 a_TexCoord;\n"
            + "uniform vec3 cameraPosition;\n"
            + "void main() {\n"
            + "    gl_Position = vec4(a_Position - cameraPosition, 1.0);\n"
            + "}\n";
        assertEquivalent("pfb-unlocated-first-in", src, GL20.GL_VERTEX_SHADER);
    }

    @Test
    void perFrameBlock_memberUniformWithInitializer() {
        GLSMHooks.perPassUniformBlock = PER_PASS;
        final String src = "#version 460 core\n"
            + "uniform float alphaTestRef = 0.1;\n"
            + "layout(location = 0) out vec4 fragColor;\n"
            + "void main() {\n"
            + "    if (fragColor.a < alphaTestRef) discard;\n"
            + "}\n";
        assertEquivalent("pfb-initializer", src, GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void perFrameBlock_firstDeclIsStrippedSampler() {
        GLSMHooks.perFrameUniformBlock = PER_FRAME;
        final String src = "#version 460 core\n"
            + "uniform sampler2D u_Unused;\n"
            + "uniform float frameTimeCounter;\n"
            + "layout(location = 0) out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = vec4(frameTimeCounter);\n"
            + "}\n";
        assertEquivalentIgnoringWhitespace("pfb-stripped-first-sampler", src, GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void perFrameBlock_shadowedMemberKeepsLocalDeclaration() {
        GLSMHooks.perFrameUniformBlock = PER_FRAME;
        final String src = "#version 460 core\n"
            + "uniform vec4 frameTimeCounter;\n"
            + "uniform vec3 cameraPosition;\n"
            + "layout(location = 0) out vec4 fragColor;\n"
            + "void main() {\n"
            + "    fragColor = frameTimeCounter + vec4(cameraPosition, 0.0);\n"
            + "}\n";
        assertEquivalent("pfb-shadowed", src, GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void perFrameBlock_noReferencesLeavesSourceUntouched() {
        GLSMHooks.perFrameUniformBlock = PER_FRAME;
        GLSMHooks.perPassUniformBlock = PER_PASS;
        final String src = "#version 460 core\n"
            + "layout(location = 0) out vec4 fragColor;\n"
            + "void main() { fragColor = vec4(1.0); }\n";
        assertEquivalent("pfb-unreferenced", src, GL20.GL_FRAGMENT_SHADER);
    }
}
