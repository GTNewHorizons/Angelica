package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/** Decides whether instrumentation is worth emitting this frame. */
public final class CaptureGate {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static final boolean IS_LINUX = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    private static final Path PROC_SELF_MAPS = Paths.get("/proc/self/maps");
    private static final String[] CAPTURE_ENV = { "RENDERDOC_CAPTURE_KEYPRESS", "ENABLE_VULKAN_RENDERDOC_CAPTURE", "RENDERDOC_HOOK_EGL" };
    private static final String[] CAPTURE_LIBS = { "renderdoc", "glxtrace", "egltrace", "apitrace" };
    public static final boolean FORCED = SystemProperties.DEBUG_MARKERS || SystemProperties.LWJGL_DEBUG;
    public static final boolean TOOL_ATTACHED = detectCaptureTool();
    public static boolean markersThisFrame = true;

    private CaptureGate() {}

    public static void refresh() {
        markersThisFrame = gateValue(FORCED, TOOL_ATTACHED, Tracy.isConnected());
    }

    static boolean gateValue(boolean forced, boolean toolAttached, boolean tracyConnected) {
        return forced || toolAttached || tracyConnected;
    }

    public static boolean enabledAtStartup() {
        return FORCED || TOOL_ATTACHED;
    }

    private static boolean detectCaptureTool() {
        if (detectFromEnv(System::getenv)) {
            LOGGER.info("Capture tool detected via environment; instrumentation enabled");
            return true;
        }
        if (!IS_LINUX) return false;
        try {
            if (detectFromMaps(Files.readAllLines(PROC_SELF_MAPS, StandardCharsets.ISO_8859_1))) {
                LOGGER.info("Capture tool library detected in process map; instrumentation enabled");
                return true;
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read {} for capture tool detection: {}", PROC_SELF_MAPS, e.toString());
        }
        return false;
    }

    static boolean detectFromEnv(UnaryOperator<String> env) {
        for (String key : CAPTURE_ENV) {
            if (env.apply(key) != null) return true;
        }
        return false;
    }

    static boolean detectFromMaps(List<String> mapLines) {
        for (int i = 0; i < mapLines.size(); i++) {
            final String line = mapLines.get(i).toLowerCase(Locale.ROOT);
            for (String lib : CAPTURE_LIBS) {
                if (line.contains(lib)) return true;
            }
        }
        return false;
    }
}
