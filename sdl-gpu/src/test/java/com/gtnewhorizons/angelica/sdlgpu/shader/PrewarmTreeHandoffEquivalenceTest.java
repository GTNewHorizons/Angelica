package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrewarmTreeHandoffEquivalenceTest {

    private static final String HEADER = "#version 460 core\n";

    @AfterEach
    void clearBlocks() {
        GLSMHooks.perFrameUniformBlock = null;
        GLSMHooks.perPassUniformBlock = null;
    }

    private static void assertHandoffEquivalent(String label, String body, int glShaderType) {
        final GLSLParser.Translation_unitContext tree = ShaderParser.parseShader(body).full();
        GlslTransformUtils.restoreReservedWordsInTree(tree);
        final String printedBody = GlslTransformUtils.getFormattedShaderRebased(tree, "");
        final String finalSource = HEADER + printedBody;

        GlslVulkanPreprocess.clearCache();
        final ShaderManager.PrewarmTransformResult reparse = ShaderManager.applyPrewarmTransformsFull(finalSource, glShaderType);
        GlslVulkanPreprocess.clearCache();
        final ShaderManager.PrewarmTransformResult treeFed = ShaderManager.applyPrewarmTransformsFull(finalSource, tree, HEADER.length(), glShaderType);

        assertEquals(reparse.source(), treeFed.source(), "tree handoff diverged from re-parse for " + label);
        assertEquals(reparse.boolUniforms(), treeFed.boolUniforms(), "bool uniforms diverged for " + label);
    }

    @Test
    void minimalFragment() {
        assertHandoffEquivalent("minimal-fragment",
            "layout(location = 0) out vec4 fragColor;\n"
                + "void main() { fragColor = vec4(1.0); }\n",
            GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void unusedSamplerStripped() {
        assertHandoffEquivalent("unused-sampler",
            "uniform sampler2D u_Unused;\n"
                + "uniform sampler2D u_Used;\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main() { fragColor = texture(u_Used, vec2(0.5)); }\n",
            GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void perFrameBlockInjection() {
        GLSMHooks.perFrameUniformBlock = new PerFrameUniformBlock(List.of(
            new Member("cameraPosition", UniformType.VEC3),
            new Member("frameTimeCounter", UniformType.FLOAT)));
        assertHandoffEquivalent("per-frame-block",
            "uniform float frameTimeCounter;\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main() { fragColor = vec4(frameTimeCounter); }\n",
            GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void vertexClipZAndUnlocatedInputs() {
        GLSMHooks.perFrameUniformBlock = new PerFrameUniformBlock(List.of(
            new Member("cameraPosition", UniformType.VEC3)));
        assertHandoffEquivalent("vertex-clipz-unlocated",
            "layout(location = 0) in vec3 a_Position;\n"
                + "in vec2 a_TexCoord;\n"
                + "uniform vec3 cameraPosition;\n"
                + "out vec2 v_TexCoord;\n"
                + "void main() {\n"
                + "    v_TexCoord = a_TexCoord;\n"
                + "    gl_Position = vec4(a_Position - cameraPosition, 1.0);\n"
                + "}\n",
            GL20.GL_VERTEX_SHADER);
    }

    @Test
    void boolUniformMetadata() {
        assertHandoffEquivalent("bool-uniform",
            "uniform bool u_Enabled;\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main() { fragColor = vec4(u_Enabled ? 1.0 : 0.0); }\n",
            GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void texelFetchPath() {
        assertHandoffEquivalent("texelfetch",
            "uniform sampler2D u_Data;\n"
                + "layout(location = 0) out vec4 fragColor;\n"
                + "void main() { fragColor = texelFetch(u_Data, ivec2(0), 0); }\n",
            GL20.GL_FRAGMENT_SHADER);
    }

    @Test
    void geometryStage() {
        assertHandoffEquivalent("geometry",
            "layout(triangles) in;\n"
                + "layout(triangle_strip, max_vertices = 3) out;\n"
                + "void main() {\n"
                + "    for (int i = 0; i < 3; i++) {\n"
                + "        gl_Position = gl_in[i].gl_Position;\n"
                + "        EmitVertex();\n"
                + "    }\n"
                + "    EndPrimitive();\n"
                + "}\n",
            GL32.GL_GEOMETRY_SHADER);
    }
}
