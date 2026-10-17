package com.gtnewhorizons.angelica.glsm.backend;

import com.google.common.primitives.Ints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class BackendStartGuard {

    private static final Logger LOGGER = LogManager.getLogger("Angelica/BackendStartGuard");

    static final int STRIKE_LIMIT = 3;

    private static File marker;
    private static File verified;
    private static String key;
    private static int strikes;
    private static boolean pending;
    private static boolean tripped;

    private BackendStartGuard() {}

    public static boolean begin(File dir, boolean sdlGpuConfigured, boolean flagForced, int requirementsRevision, String requestedDriver) {
        marker = new File(dir, "sdlgpu-starting");
        verified = new File(dir, "sdlgpu-verified");
        key = requirementsRevision + "/" + requestedDriver;
        pending = tripped = false;
        if (flagForced) return false;
        if (!sdlGpuConfigured) {
            delete(marker);
            return false;
        }
        if (key.equals(read(verified))) return false;
        final String count = read(marker);
        final Integer parsed = count == null ? null : Ints.tryParse(count.trim());
        strikes = parsed == null ? 0 : parsed;
        if (strikes >= STRIKE_LIMIT) {
            LOGGER.warn("SDL GPU failed to bring up the device {} times in a row; resetting the render backend to OpenGL", strikes);
            delete(marker);
            tripped = true;
            return true;
        }
        if (strikes == STRIKE_LIMIT - 1) {
            LOGGER.warn("SDL GPU failed to bring up the device {} times in a row; one more and the render backend switches to OpenGL", strikes);
        } else if (strikes > 0) {
            LOGGER.info("The last start ended while bringing up the SDL GPU device");
        }
        pending = true;
        return false;
    }

    public static void arm() {
        if (pending) write(marker, Integer.toString(strikes + 1));
    }

    public static void disarm() {
        if (pending) delete(marker);
    }

    public static void latch() {
        if (!pending) return;
        pending = false;
        write(verified, key);
        LOGGER.info("SDL GPU booted; the startup guard stays off until the requirements revision or driver changes");
    }

    public static boolean tripped() {
        return tripped;
    }

    private static String read(File file) {
        try {
            return file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            LOGGER.warn("Could not read {}: {}", file, e.toString());
            return null;
        }
    }

    private static void write(File file, String content) {
        try {
            Files.createDirectories(file.toPath().getParent());
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private static void delete(File file) {
        if (file.isFile() && !file.delete()) LOGGER.warn("Could not delete {}", file);
    }
}
