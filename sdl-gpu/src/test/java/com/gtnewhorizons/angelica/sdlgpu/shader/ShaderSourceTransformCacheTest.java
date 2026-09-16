package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderSourceTransformCacheTest {

    private static final String FRAG = """
        #version 460 core
        uniform sampler2D u_Unused;
        uniform sampler2D u_Used;
        layout(location = 0) out vec4 fragColor;
        void main() {
            fragColor = texture(u_Used, vec2(0.5));
        }
        """;

    private static final String VERT = """
        #version 460 core
        layout(location = 0) in vec3 a_Position;
        in vec2 a_TexCoord;
        out vec2 v_TexCoord;
        void main() {
            v_TexCoord = a_TexCoord;
            gl_Position = vec4(a_Position, 1.0);
        }
        """;

    private static final String PFB_FRAG = """
        #version 460 core
        uniform float frameTimeCounter;
        layout(location = 0) out vec4 fragColor;
        void main() {
            fragColor = vec4(frameTimeCounter);
        }
        """;

    private static final String CLIP_Z = "gl_Position.z = gl_Position.z * 0.5 + gl_Position.w * 0.5;";

    @BeforeEach
    void reset() {
        ShaderManager.clearPrewarmCache();
        GlslVulkanPreprocess.clearCache();
    }

    @AfterEach
    void clearBlocks() {
        ShaderManager.clearPrewarmCache();
        GlslVulkanPreprocess.clearCache();
        GLSMHooks.perFrameUniformBlock = null;
        GLSMHooks.perPassUniformBlock = null;
    }

    @Test
    void repeatedSourceReusesTheChainedTransform() {
        final String expected = ShaderTransformChain.run(FRAG, GL20.GL_FRAGMENT_SHADER);
        GlslVulkanPreprocess.clearCache();

        final ShaderManager sm = new ShaderManager(null);
        final int a = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        final int b = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(a, FRAG);
        sm.shaderSource(b, FRAG);

        assertEquals(expected, sm.getShaderSource(a));
        assertSame(sm.getShaderSource(a), sm.getShaderSource(b));
    }

    @Test
    void vertexSourceMatchesTheChainAndCarriesClipZ() {
        final String expected = ShaderTransformChain.run(VERT, GL20.GL_VERTEX_SHADER);
        GlslVulkanPreprocess.clearCache();

        final ShaderManager sm = new ShaderManager(null);
        final int v = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(v, VERT);

        assertEquals(expected, sm.getShaderSource(v));
        assertTrue(sm.getShaderSource(v).contains(CLIP_Z));
    }

    @Test
    void blockChangeMissesTheCache() {
        GLSMHooks.perFrameUniformBlock = new PerFrameUniformBlock(List.of(new Member("frameTimeCounter", UniformType.FLOAT)));

        final ShaderManager sm = new ShaderManager(null);
        final int withBlock = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(withBlock, PFB_FRAG);
        assertTrue(sm.getShaderSource(withBlock).contains("AngelicaPerFrame"));

        GLSMHooks.perFrameUniformBlock = null;
        GlslVulkanPreprocess.clearCache();
        final String expected = ShaderTransformChain.run(PFB_FRAG, GL20.GL_FRAGMENT_SHADER);
        GlslVulkanPreprocess.clearCache();

        final int withoutBlock = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(withoutBlock, PFB_FRAG);

        assertEquals(expected, sm.getShaderSource(withoutBlock));
        assertFalse(sm.getShaderSource(withoutBlock).contains("AngelicaPerFrame"));
    }
}
