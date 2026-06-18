package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;

/**
 * 3.3 core-profile context extension
 */
public class GLSMCoreExtension implements BeforeAllCallback, AfterAllCallback {
    private boolean ownsDisplay = false;

    @Override
    public void beforeAll(ExtensionContext context) throws LWJGLException {
        if (Display.isCreated()) return;
        Display.setDisplayModeAndFullscreen(new DisplayMode(800, 600));
        Display.setResizable(false);
        Display.setFullscreen(false);
        Display.create(
            new PixelFormat().withDepthBits(24).withStencilBits(8),
            new ContextAttribs(3, 3).withProfileCore(true).withForwardCompatible(true));
        ownsDisplay = true;
        GLStateManager.initialize(GLSMInitConfig.builder().displaySize(800, 600).build());
        GLStateManager.setRunningSplash(false);
        GLStateManager.markSplashComplete();
        GLStateManager.BYPASS_CACHE = false;
        QuadConverter.invalidateEBO();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (ownsDisplay && Display.isCreated()) Display.destroy();
        ownsDisplay = false;
    }
}
