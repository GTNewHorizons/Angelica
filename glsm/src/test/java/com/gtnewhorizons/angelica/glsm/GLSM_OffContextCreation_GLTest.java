package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.SharedDrawable;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@GLCoreTest
public class GLSM_OffContextCreation_GLTest {

    private SharedDrawable sharedDrawable;

    @AfterEach
    void cleanup() {
        if (sharedDrawable != null) {
            DrawableContexts.destroy(sharedDrawable);
            sharedDrawable = null;
        }
    }

    private static Map<String, Executable> creationCalls() {
        final Map<String, Executable> calls = new LinkedHashMap<>();
        calls.put("glGenTextures()", GLSMTextures::glGenTextures);
        calls.put("glGenTextures(IntBuffer)", () -> GLSMTextures.glGenTextures(BufferUtils.createIntBuffer(1)));
        calls.put("glGenSamplers()", GLSMTextures::glGenSamplers);
        calls.put("glGenSamplers(IntBuffer)", () -> GLSMTextures.glGenSamplers(BufferUtils.createIntBuffer(1)));
        calls.put("glGenSamplers(int[])", () -> GLSMTextures.glGenSamplers(new int[1]));
        calls.put("glGenBuffers()", GLSMBuffers::glGenBuffers);
        calls.put("glGenBuffers(IntBuffer)", () -> GLSMBuffers.glGenBuffers(BufferUtils.createIntBuffer(1)));
        calls.put("glGenBuffers(int[])", () -> GLSMBuffers.glGenBuffers(new int[1]));
        calls.put("glCreateBuffers()", GLSMBuffers::glCreateBuffers);
        calls.put("glCreateBuffers(IntBuffer)", () -> GLSMBuffers.glCreateBuffers(BufferUtils.createIntBuffer(1)));
        calls.put("glCreateBuffers(int[])", () -> GLSMBuffers.glCreateBuffers(new int[1]));
        calls.put("glGenVertexArrays()", GLSMVertexInput::glGenVertexArrays);
        calls.put("glGenFramebuffers()", GLSMFramebuffer::glGenFramebuffers);
        calls.put("glGenRenderbuffers()", GLSMFramebuffer::glGenRenderbuffers);
        calls.put("glGenQueries()", GLSMQueries::glGenQueries);
        calls.put("glGenQueries(IntBuffer)", () -> GLSMQueries.glGenQueries(BufferUtils.createIntBuffer(1)));
        calls.put("glCreateShader", () -> GLSMPrograms.glCreateShader(GL20.GL_VERTEX_SHADER));
        calls.put("glCreateProgram", GLSMPrograms::glCreateProgram);
        calls.put("RenderSystem.createTexture", () -> RenderSystem.createTexture(GL11.GL_TEXTURE_2D));
        calls.put("RenderSystem.createFramebuffer", RenderSystem::createFramebuffer);
        return calls;
    }

    @Test
    void creationWithoutContextThrows() throws InterruptedException {
        TestThreads.run("glsm-no-context", () -> {
            for (Map.Entry<String, Executable> call : creationCalls().entrySet()) {
                assertThrows(IllegalStateException.class, call.getValue(), call.getKey() + " did not throw off-context");
            }
        });
    }

    @Test
    void creationOnSharedContextThreadPasses() throws Exception {
        sharedDrawable = new SharedDrawable(Display.getDrawable());
        DrawableContexts.run(sharedDrawable, "glsm-shared-context", () -> {
            final int texture = GLSMTextures.glGenTextures();
            assertNotEquals(0, texture, "no texture name on a thread that owns a shared context");
            GLSMTextures.glDeleteTextures(texture);
        });
    }
}
