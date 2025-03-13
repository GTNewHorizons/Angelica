package com.gtnewhorizons.angelica;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.managers.GLLightingManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class OpenGLTestBase {
    protected static long window;
    protected static GLFWErrorCallback errorCallback;
    protected static GLCapabilities capabilities;
    protected static String glVendor;
    protected static String glRenderer;
    protected static String glVersion;

    @BeforeAll
    public void setupOpenGL() {
        // Set system property to indicate we're in a test environment
        // This prevents HUDCaching from trying to initialize Minecraft classes
        System.setProperty("angelica.test.environment", "true");

        // Enable LWJGL debug mode
        Configuration.DEBUG.set(true);
        Configuration.DEBUG_MEMORY_ALLOCATOR.set(true);
        Configuration.DEBUG_STACK.set(true);

        // Initialize GLFW
        errorCallback = GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Create window and OpenGL context
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);

            // Request a compatibility profile with OpenGL 3.3
            // This is important for deprecated functions like glPushAttrib
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_FALSE); // Don't use forward compatibility for compat profile
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE);

            // Create window
            window = GLFW.glfwCreateWindow(800, 600, "OpenGL Test Context", 0, 0);
            if (window == 0) {
                throw new RuntimeException("Failed to create GLFW window");
            }

            // Center window
            final GLFWVidMode vidMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
            if (vidMode != null) {
                GLFW.glfwSetWindowPos(window,
                    (vidMode.width() - 800) / 2,
                    (vidMode.height() - 600) / 2
                );
            }

            // Make context current
            GLFW.glfwMakeContextCurrent(window);
            capabilities = GL.createCapabilities();
            assertNotNull(capabilities, "Failed to create OpenGL capabilities");

            // Enable debug output if available
            if (capabilities.GL_KHR_debug) {
                GL11.glEnable(GL43.GL_DEBUG_OUTPUT);
                GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
                System.out.println("Debug context enabled - using glGetError() for error detection");
            }

            // Store GL info
            glVendor = GL11.glGetString(GL11.GL_VENDOR);
            glRenderer = GL11.glGetString(GL11.GL_RENDERER);
            glVersion = GL11.glGetString(GL11.GL_VERSION);

            System.out.println("OpenGL Test Context:");
            System.out.println("Vendor: " + glVendor);
            System.out.println("Renderer: " + glRenderer);
            System.out.println("Version: " + glVersion);
            System.out.println("Debug Context: " + (capabilities.GL_KHR_debug ? "Yes" : "No"));

            // Verify we have a compatibility profile
            if (!glVersion.contains("Compatibility")) {
                System.out.println("WARNING: OpenGL context does not appear to be a compatibility profile!");
                System.out.println("Deprecated functions like glPushAttrib may not work correctly.");
            }

            // Initialize GLStateManager
            GLStateManager.reset();
        }
    }

    @BeforeAll
    public static void initializeGLStateManager() {
        // Initialize GLStateManager
        GLStateManager.preInit();
        GLStateManager.setRunningSplash(false);
        GLStateManager.BYPASS_CACHE = false;
        RenderSystem.initRenderer();
    }


    @BeforeEach
    public void resetOpenGLState() {
        // Reset OpenGL state before each test
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_NORMALIZE);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // Reset color
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // Reset material
        final FloatBuffer defaultAmbient = BufferUtils.createFloatBuffer(4);
        defaultAmbient.put(new float[]{0.2f, 0.2f, 0.2f, 1.0f});
        defaultAmbient.flip();
        GLLightingManager.glMaterialfv(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT, defaultAmbient);

        // Reset GLStateManager
        GLStateManager.reset();
    }

    @AfterAll
    public void tearDownOpenGL() {
        if (window != 0) {
            GLFW.glfwDestroyWindow(window);
            window = 0;
        }

        if (errorCallback != null) {
            errorCallback.free();
            errorCallback = null;
        }

        GLFW.glfwTerminate();
    }
}
