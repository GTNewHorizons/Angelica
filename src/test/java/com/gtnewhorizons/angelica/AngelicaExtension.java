package com.gtnewhorizons.angelica;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

public class AngelicaExtension implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static boolean started = false;
    private static DisplayMode displayMode;

    @Override
    public void beforeAll(ExtensionContext context) throws LWJGLException {
        if (!started) {
            started = true;

            displayMode = findDisplayMode(800, 600, Display.getDisplayMode().getBitsPerPixel());
            Display.setDisplayModeAndFullscreen(displayMode);
            Display.setFullscreen(false);
            final PixelFormat format = new PixelFormat().withDepthBits(24);
            Display.create(format);

            // Warm-up State Manager features
            GLStateManager.preInit();

            context.getRoot().getStore(GLOBAL).put("AngelicaExtension", this);
        }
    }

    @Override
    public void close() {
        Display.destroy();
    }

    private static DisplayMode findDisplayMode(int width, int height, int bpp) throws LWJGLException {
        final DisplayMode[] modes = Display.getAvailableDisplayModes();
        for ( DisplayMode mode : modes ) {
            if ( mode.getWidth() == width && mode.getHeight() == height && mode.getBitsPerPixel() >= bpp && mode.getFrequency() <= 60 ) {
                return mode;
            }
        }
        return Display.getDesktopDisplayMode();
    }
}
