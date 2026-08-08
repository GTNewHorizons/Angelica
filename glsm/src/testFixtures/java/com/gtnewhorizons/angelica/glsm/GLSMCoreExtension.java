package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.PixelFormat;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * 3.3 core forward-compatible context. FFP emulation stays enabled, so per-test isolation only deactivates it.
 */
public class GLSMCoreExtension implements BeforeAllCallback, BeforeEachCallback, ExtensionContext.Store.CloseableResource {

    private static boolean started = false;
    private static Throwable startFailure;

    @Override
    public void beforeAll(ExtensionContext context) throws LWJGLException {
        if (startFailure != null) {
            throw new IllegalStateException("GLSMCoreExtension context setup failed earlier in this JVM", startFailure);
        }
        if (started) return;
        try {
            Display.setDisplayModeAndFullscreen(new DisplayMode(800, 600));
            Display.setResizable(false);
            Display.setFullscreen(false);
            Display.create(
                new PixelFormat().withDepthBits(24).withStencilBits(8),
                new ContextAttribs(3, 3).withProfileCore(true).withForwardCompatible(true));

            GLSMExtension.setMainThread(Thread.currentThread());
            GLStateManager.initialize(GLSMInitConfig.builder().displaySize(800, 600).build());
            GLStateManager.setRunningSplash(false);
            GLStateManager.markSplashComplete("test");

            GLSMExtension.glVendor = GL11.glGetString(GL11.GL_VENDOR);
            GLSMExtension.glRenderer = GL11.glGetString(GL11.GL_RENDERER);
            GLSMExtension.glVersion = GL11.glGetString(GL11.GL_VERSION);
            System.out.println("OpenGL Vendor: " + GLSMExtension.glVendor);
            System.out.println("OpenGL Renderer: " + GLSMExtension.glRenderer);
            System.out.println("OpenGL Version: " + GLSMExtension.glVersion);

            final int profileMask = GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK);
            if ((profileMask & GL32.GL_CONTEXT_CORE_PROFILE_BIT) == 0) {
                throw new IllegalStateException("Expected a GL core context, got profile mask 0x" + Integer.toHexString(profileMask) + " (" + GLSMExtension.glVersion + ")");
            }
            context.getRoot().getStore(GLOBAL).put("GLSMCoreExtension", this);
            started = true;
        } catch (LWJGLException | RuntimeException | Error e) {
            startFailure = e;
            throw e;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
        final ShaderManager ffp = ShaderManager.getInstance();
        if (ffp.isActive()) {
            ffp.deactivate();
        }
    }

    @Override
    public void close() {
        if (Display.isCreated()) Display.destroy();
    }
}
