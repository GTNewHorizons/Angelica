package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrewarmSingleParseEquivalenceTest {

    @AfterEach
    void clearBlocks() {
        GLSMHooks.perFrameUniformBlock = null;
        GLSMHooks.perPassUniformBlock = null;
    }

    private static final PerFrameUniformBlock PER_FRAME = new PerFrameUniformBlock(List.of(
        new Member("cameraPosition", UniformType.VEC3),
        new Member("frameTimeCounter", UniformType.FLOAT)));

    private static final PerFrameUniformBlock PER_PASS = new PerFrameUniformBlock(List.of(
        new Member("alphaTestRef", UniformType.FLOAT)));

    private static Arguments shader(String name, int glShaderType, PerFrameUniformBlock perFrame, PerFrameUniformBlock perPass, boolean exact, String source) {
        return Arguments.of(name, glShaderType, perFrame, perPass, exact, source);
    }

    static Stream<Arguments> shaders() {
        return Stream.of(
            shader("minimalFragment_noTransformsNeeded", GL20.GL_FRAGMENT_SHADER, null, null, true,
                "#version 460 core\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() { fragColor = vec4(1.0); }\n"),
            shader("minimalVertex_depthRemapInjected", GL20.GL_VERTEX_SHADER, null, null, true,
                "#version 460 core\n"
                    + "layout(location = 0) in vec3 a_Position;\n"
                    + "void main() { gl_Position = vec4(a_Position, 1.0); }\n"),
            shader("fragmentWithUnusedSampler_strippedAndIdsRewritten", GL20.GL_FRAGMENT_SHADER, null, null, true,
                "#version 460 core\n"
                    + "uniform sampler2D u_Unused;\n"
                    + "uniform sampler2D u_Used;\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "    fragColor = texture(u_Used, vec2(0.5));\n"
                    + "}\n"),
            shader("vertexWithUnlocatedInputs_layoutLocationsInserted", GL20.GL_VERTEX_SHADER, null, null, true,
                "#version 460 core\n"
                    + "layout(location = 0) in vec3 a_Position;\n"
                    + "in vec2 a_TexCoord;\n"
                    + "in vec3 a_Normal;\n"
                    + "out vec2 v_TexCoord;\n"
                    + "void main() {\n"
                    + "    v_TexCoord = a_TexCoord;\n"
                    + "    gl_Position = vec4(a_Position + a_Normal, 1.0);\n"
                    + "}\n"),
            shader("vertexWithBuiltinIdRewrites", GL20.GL_VERTEX_SHADER, null, null, true,
                "#version 460 core\n"
                    + "void main() {\n"
                    + "    int v = gl_VertexID;\n"
                    + "    int i = gl_InstanceID;\n"
                    + "    gl_Position = vec4(float(v + i), 0.0, 0.0, 1.0);\n"
                    + "}\n"),
            shader("fragmentWithBoolUniformsAndSamplerRename", GL20.GL_FRAGMENT_SHADER, null, null, true,
                "#version 460 core\n"
                    + "uniform bool u_Enabled;\n"
                    + "uniform float u_Gain = 1.5;\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "    fragColor = vec4(u_Enabled ? u_Gain : 0.0);\n"
                    + "}\n"),
            shader("samplerOnlyReferencedInsidePreprocessorBlock_preservedConsistently", GL20.GL_FRAGMENT_SHADER, null, null, true,
                "#version 460 core\n"
                    + "#define FOO 1\n"
                    + "layout(binding = 0) uniform sampler2D u_S;\n"
                    + "void main() {\n"
                    + "#if FOO\n"
                    + "    float a = textureLod(u_S, vec2(0), 0.0).r;\n"
                    + "#endif\n"
                    + "}\n"),
            shader("compute_noVertexOnlyPasses", GL43.GL_COMPUTE_SHADER, null, null, true,
                "#version 460 core\n"
                    + "layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;\n"
                    + "uniform sampler2D u_Unused;\n"
                    + "void main() {}\n"),
            shader("geometry_noVertexOnlyPasses", GL32.GL_GEOMETRY_SHADER, null, null, true,
                "#version 460 core\n"
                    + "layout(triangles) in;\n"
                    + "layout(triangle_strip, max_vertices = 3) out;\n"
                    + "void main() {\n"
                    + "    for (int i = 0; i < 3; i++) {\n"
                    + "        gl_Position = gl_in[i].gl_Position;\n"
                    + "        EmitVertex();\n"
                    + "    }\n"
                    + "    EndPrimitive();\n"
                    + "}\n"),
            shader("readOnlyImage_rewrittenToTextureWithSamplerlessExtension", GL20.GL_FRAGMENT_SHADER, null, null, true,
                "#version 460 core\n"
                    + "layout(binding = 2, rgba8) uniform readonly image2D u_Img;\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "    fragColor = imageLoad(u_Img, ivec2(1, 2)) + vec4(imageSize(u_Img), 0.0, 1.0);\n"
                    + "}\n"),
            shader("samplerlessTexture_extensionInserted", GL20.GL_FRAGMENT_SHADER, null, null, true,
                "#version 460 core\n"
                    + "uniform texture2D u_Tex;\n"
                    + "uniform sampler u_Smp;\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "    fragColor = texelFetch(u_Tex, ivec2(0), 0) + texture(sampler2D(u_Tex, u_Smp), vec2(0.5));\n"
                    + "}\n"),
            shader("unparseable_fallsBackToInputUnchanged", GL20.GL_FRAGMENT_SHADER, null, null, true,
                "this is not valid GLSL @@@@\n"),
            shader("perFrameBlock_replaceableUniformStrippedAndInjected", GL20.GL_FRAGMENT_SHADER, PER_FRAME, PER_PASS, true,
                "#version 460 core\n"
                    + "uniform float frameTimeCounter;\n"
                    + "uniform float alphaTestRef;\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "    if (fragColor.a < alphaTestRef) discard;\n"
                    + "    fragColor = vec4(frameTimeCounter);\n"
                    + "}\n"),
            shader("perFrameBlock_firstDeclIsUnlocatedVsInput", GL20.GL_VERTEX_SHADER, PER_FRAME, null, true,
                "#version 460 core\n"
                    + "in vec3 a_Position;\n"
                    + "layout(location = 5) in vec2 a_TexCoord;\n"
                    + "uniform vec3 cameraPosition;\n"
                    + "void main() {\n"
                    + "    gl_Position = vec4(a_Position - cameraPosition, 1.0);\n"
                    + "}\n"),
            shader("perFrameBlock_memberUniformWithInitializer", GL20.GL_FRAGMENT_SHADER, null, PER_PASS, true,
                "#version 460 core\n"
                    + "uniform float alphaTestRef = 0.1;\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "    if (fragColor.a < alphaTestRef) discard;\n"
                    + "}\n"),
            shader("perFrameBlock_firstDeclIsStrippedSampler", GL20.GL_FRAGMENT_SHADER, PER_FRAME, null, false,
                "#version 460 core\n"
                    + "uniform sampler2D u_Unused;\n"
                    + "uniform float frameTimeCounter;\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "    fragColor = vec4(frameTimeCounter);\n"
                    + "}\n"),
            shader("perFrameBlock_shadowedMemberKeepsLocalDeclaration", GL20.GL_FRAGMENT_SHADER, PER_FRAME, null, true,
                "#version 460 core\n"
                    + "uniform vec4 frameTimeCounter;\n"
                    + "uniform vec3 cameraPosition;\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "    fragColor = frameTimeCounter + vec4(cameraPosition, 0.0);\n"
                    + "}\n"),
            shader("perFrameBlock_noReferencesLeavesSourceUntouched", GL20.GL_FRAGMENT_SHADER, PER_FRAME, PER_PASS, true,
                "#version 460 core\n"
                    + "layout(location = 0) out vec4 fragColor;\n"
                    + "void main() { fragColor = vec4(1.0); }\n"));
    }

    /** {@code exact == false} compares modulo whitespace. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("shaders")
    void singleParseMatchesChainedPipeline(String name, int glShaderType, PerFrameUniformBlock perFrame, PerFrameUniformBlock perPass, boolean exact, String source) {
        GLSMHooks.perFrameUniformBlock = perFrame;
        GLSMHooks.perPassUniformBlock = perPass;

        GlslVulkanPreprocess.clearCache();
        final String legacyOut = ShaderTransformChain.run(source, glShaderType);
        GlslVulkanPreprocess.clearCache();
        final String parseOnceOut = ShaderManager.applyPrewarmTransformsFull(source, glShaderType, GLSMHooks.perFrameUniformBlock, GLSMHooks.perPassUniformBlock).source();

        if (exact) {
            assertEquals(legacyOut, parseOnceOut, "single-parse vs chained pipeline diverged for " + name);
        } else {
            assertEquals(legacyOut.replaceAll("\\s+", " "), parseOnceOut.replaceAll("\\s+", " "), "single-parse vs chained pipeline diverged beyond whitespace for " + name);
        }
    }
}
