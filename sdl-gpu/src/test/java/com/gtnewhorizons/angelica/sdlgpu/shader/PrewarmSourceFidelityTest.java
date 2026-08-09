package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;

import static org.junit.jupiter.api.Assertions.*;

class PrewarmSourceFidelityTest {

    private static final String RAW = """
        #version 460 core
        in vec3 a_PosId;
        void main() {
            gl_Position = vec4(a_PosId, 1.0);
        }
        """;

    private static final String CLIP_Z = "gl_Position.z = gl_Position.z * 0.5 + gl_Position.w * 0.5;";

    @BeforeEach
    void reset() {
        ShaderManager.clearPrewarmCache();
    }

    @Test
    void prewarmHitStoresTheTransformedSourceNotTheRawInput() {
        ShaderManager.prewarmSpirv(RAW, GL20.GL_VERTEX_SHADER);

        final ShaderManager sm = new ShaderManager(null);
        final int shader = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(shader, RAW);

        final String stored = sm.getShaderSource(shader);
        assertNotEquals(RAW, stored, "a prewarm hit must not leave the raw pre-transform source on the shader");
        assertTrue(stored.contains(CLIP_Z), "stored source must carry the GL->Vulkan depth remap that the cached SPIR-V was built with\n\n" + stored);
    }

    @Test
    void prewarmMissAlsoStoresTheTransformedSource() {
        final ShaderManager sm = new ShaderManager(null);
        final int shader = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(shader, RAW);

        final String stored = sm.getShaderSource(shader);
        assertTrue(stored.contains(CLIP_Z), "the non-prewarm path must agree with the prewarm path\n\n" + stored);
    }
}
