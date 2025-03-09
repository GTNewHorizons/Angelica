package com.gtnewhorizons.angelica;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.lwjgl.Version;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LWJGLTest extends OpenGLTestBase {

    @Test
    public void testLWJGLVersion() {
        // Check that LWJGL version is as expected
        String version = Version.getVersion();
        assertTrue(version.startsWith("3.3.3"), "LWJGL version mismatch: expected 3.3.3, got " + version);
    }

    @Test
    public void testGLFWInitialization() {
        // Check that GLFW is initialized
        assertNotNull(capabilities, "OpenGL capabilities should be initialized");
        assertNotNull(glVendor, "OpenGL vendor should be available");
        assertNotNull(glVersion, "OpenGL version should be available");
    }
}
