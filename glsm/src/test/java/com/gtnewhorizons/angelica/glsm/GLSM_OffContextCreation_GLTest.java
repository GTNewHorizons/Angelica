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
        calls.put("glGenTextures()", GLStateManager::glGenTextures);
        calls.put("glGenTextures(IntBuffer)", () -> GLStateManager.glGenTextures(BufferUtils.createIntBuffer(1)));
        calls.put("glGenSamplers()", GLStateManager::glGenSamplers);
        calls.put("glGenSamplers(IntBuffer)", () -> GLStateManager.glGenSamplers(BufferUtils.createIntBuffer(1)));
        calls.put("glGenSamplers(int[])", () -> GLStateManager.glGenSamplers(new int[1]));
        calls.put("glGenBuffers()", GLStateManager::glGenBuffers);
        calls.put("glGenBuffers(IntBuffer)", () -> GLStateManager.glGenBuffers(BufferUtils.createIntBuffer(1)));
        calls.put("glGenBuffers(int[])", () -> GLStateManager.glGenBuffers(new int[1]));
        calls.put("glCreateBuffers()", GLStateManager::glCreateBuffers);
        calls.put("glCreateBuffers(IntBuffer)", () -> GLStateManager.glCreateBuffers(BufferUtils.createIntBuffer(1)));
        calls.put("glCreateBuffers(int[])", () -> GLStateManager.glCreateBuffers(new int[1]));
        calls.put("glGenVertexArrays()", GLStateManager::glGenVertexArrays);
        calls.put("glGenFramebuffers()", GLStateManager::glGenFramebuffers);
        calls.put("glGenRenderbuffers()", GLStateManager::glGenRenderbuffers);
        calls.put("glGenQueries()", GLStateManager::glGenQueries);
        calls.put("glGenQueries(IntBuffer)", () -> GLStateManager.glGenQueries(BufferUtils.createIntBuffer(1)));
        calls.put("glCreateShader", () -> GLStateManager.glCreateShader(GL20.GL_VERTEX_SHADER));
        calls.put("glCreateProgram", GLStateManager::glCreateProgram);
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
            final int texture = GLStateManager.glGenTextures();
            assertNotEquals(0, texture, "no texture name on a thread that owns a shared context");
            GLStateManager.glDeleteTextures(texture);
        });
    }
}
