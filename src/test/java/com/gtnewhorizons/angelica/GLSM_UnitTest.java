package com.gtnewhorizons.angelica;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

class GLSM_UnitTest {
    private static DisplayMode mode;
    @BeforeAll
    static void setup() throws LWJGLException {
        mode = findDisplayMode(800, 600, Display.getDisplayMode().getBitsPerPixel());
        Display.setDisplayModeAndFullscreen(mode);
    }

    @AfterAll
    static void cleanup() {
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
    @Test
    void PushPopAttribTest() {
        // TODO
        assert(true);
    }
}
