package com.gtnewhorizons.angelica.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

/**
 * Checks for known problematic driver configurations and applies workarounds. Adapted from Embeddium/Sodium's LateDriverScanner.
 *
 * <p>The NVIDIA workaround can be disabled via: {@code -Dangelica.disableNvidiaWorkaround=true}
 */
public class DriverCompatabilityCheck {
    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private static final boolean DISABLE_NVIDIA_WORKAROUND = Boolean.getBoolean("angelica.disableNvidiaWorkaround");

    /**
     * Should be called after GL context is created and GLStateManager is initialized.
     */
    public static void checkDriverCompatibility() {
        final String vendor = GL11.glGetString(GL11.GL_VENDOR);
        final String renderer = GL11.glGetString(GL11.GL_RENDERER);
        final String version = GL11.glGetString(GL11.GL_VERSION);

        LOGGER.info("OpenGL Vendor: {}", vendor);
        LOGGER.info("OpenGL Renderer: {}", renderer);
        LOGGER.info("OpenGL Version: {}", version);

        if (vendor != null && vendor.contains("NVIDIA")) {
            if (DISABLE_NVIDIA_WORKAROUND) {
                LOGGER.info("NVIDIA workaround disabled via -Dangelica.disableNvidiaWorkaround=true");
            } else {
                applyNvidiaWorkaround();
            }
        }
    }

    /**
     * https://github.com/godotengine/godot/issues/33969#issuecomment-917846774
     * Attempt to force the driver to disable its submission thread by enabling synchronous debug output.
     * This usually suppresses bad behavior from NVIDIA's "Threaded Optimizations" feature.
     */
    private static void applyNvidiaWorkaround() {
        if (!GLStateManager.capabilities.GL_ARB_debug_output) {
            LOGGER.warn("Cannot apply NVIDIA workaround: ARB_debug_output not available");
            return;
        }

        LOGGER.info("Enabling workaround for NVIDIA threaded optimizations");
        GL11.glEnable(ARBDebugOutput.GL_DEBUG_OUTPUT_SYNCHRONOUS_ARB);
    }
}
