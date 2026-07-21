package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLVersionParseTest {

    @Test
    void parseMajorMinor() {
        assertEquals(43, RenderSystem.parseMajorMinor("4.3.0 - Build 20.19.15.4835"));
        assertEquals(46, RenderSystem.parseMajorMinor("4.6.0 NVIDIA 551.61"));
        assertEquals(41, RenderSystem.parseMajorMinor("4.1 Metal - 88"));
        assertEquals(33, RenderSystem.parseMajorMinor("3.3"));
        assertEquals(46, RenderSystem.parseMajorMinor("4.6 (Core Profile) Mesa 24.0.3"));
        assertEquals(-1, RenderSystem.parseMajorMinor(null));
        assertEquals(-1, RenderSystem.parseMajorMinor(""));
        assertEquals(-1, RenderSystem.parseMajorMinor("garbage"));
        assertEquals(-1, RenderSystem.parseMajorMinor("OpenGL ES 3.2"));
    }

    @Test
    void isContextValid() {
        assertTrue(RenderSystem.isContextValid(4, 4, 46, true));
        assertTrue(RenderSystem.isContextValid(4, 4, 44, true));
        assertFalse(RenderSystem.isContextValid(4, 4, 43, true));
        assertFalse(RenderSystem.isContextValid(4, 4, 44, false));
        assertTrue(RenderSystem.isContextValid(4, 4, -1, true));
        assertFalse(RenderSystem.isContextValid(4, 4, -1, false));
        assertTrue(RenderSystem.isContextValid(3, 3, 33, true));
    }
}
