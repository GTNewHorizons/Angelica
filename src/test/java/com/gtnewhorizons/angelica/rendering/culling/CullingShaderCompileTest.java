package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL43;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class CullingShaderCompileTest {

    private static String source(String name) throws IOException {
        try (InputStream in = CullingShaderCompileTest.class.getResourceAsStream("/assets/angelica/shaders/culling/" + name)) {
            assertNotNull(in, "missing shader resource: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertCompiles(String name) throws IOException {
        assumeTrue(RenderSystem.supportsCompute(), "compute shaders unsupported");
        GLStateManager.glDeleteShader(GlTestSupport.compile(GL43.GL_COMPUTE_SHADER, source(name)));
    }

    @Test
    void chunkCullCompiles() throws IOException {
        assertCompiles("chunk_cull.csh");
    }
}
