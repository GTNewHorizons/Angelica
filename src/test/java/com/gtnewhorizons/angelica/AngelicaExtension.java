package com.gtnewhorizons.angelica;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

public class AngelicaExtension implements BeforeAllCallback, AfterEachCallback, ExtensionContext.Store.CloseableResource {

    private static boolean started = false;
    private static DisplayMode displayMode;
    public static String glVendor;
    public static String glRenderer;
    public static String glVersion;

    @Override
    public void beforeAll(ExtensionContext context) throws LWJGLException {
        if (!started) {
            started = true;

            displayMode = new DisplayMode(800, 600);
            Display.setDisplayModeAndFullscreen(displayMode);
            Display.setResizable(false);
            Display.setFullscreen(false);
            final PixelFormat format = new PixelFormat().withDepthBits(24).withStencilBits(8);
            Display.create(format);

            // Set MainThread to the current test thread (the one with the GL context)
            setMainThread(Thread.currentThread());

            // Warm-up State Manager features
            GLStateManager.preInit();
            GLStateManager.setRunningSplash(false); // So we don't bypass the cache
            GLStateManager.markSplashComplete(); // Enable caching for tests
            GLStateManager.BYPASS_CACHE = false; // Just to be sure
            RenderSystem.initRenderer();
            context.getRoot().getStore(GLOBAL).put("AngelicaExtension", this);
            glVendor = GL11.glGetString(GL11.GL_VENDOR);
            glRenderer = GL11.glGetString(GL11.GL_RENDERER);
            glVersion = GL11.glGetString(GL11.GL_VERSION);

            System.out.println("OpenGL Vendor: " + glVendor);
            System.out.println("OpenGL Renderer: " + glRenderer);
            System.out.println("OpenGL Version: " + glVersion);
        }
    }

    private static void setMainThread(Thread thread) {
        try {
            // Use Unsafe to set final static field - works on all Java versions
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Field mainThreadField = GLStateManager.class.getDeclaredField("MainThread");
            Object base = unsafe.staticFieldBase(mainThreadField);
            long offset = unsafe.staticFieldOffset(mainThreadField);
            unsafe.putObject(base, offset, thread);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set MainThread for tests", e);
        }
    }

    @Override
    public void close() {
        Display.destroy();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "GL Error");
    }
}
