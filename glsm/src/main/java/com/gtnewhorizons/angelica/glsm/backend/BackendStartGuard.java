package com.gtnewhorizons.angelica.glsm.backend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public final class BackendStartGuard {

    private static final Logger LOGGER = LogManager.getLogger("Angelica/BackendStartGuard");

    private static File pending;
    private static boolean tripped;

    private BackendStartGuard() {}

    public static boolean begin(File marker, boolean sdlGpuConfigured, boolean flagForced) {
        pending = null;
        tripped = false;
        if (!sdlGpuConfigured || flagForced) {
            delete(marker);
            return false;
        }
        if (marker.isFile()) {
            delete(marker);
            tripped = true;
            return true;
        }
        try {
            final File dir = marker.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            if (marker.createNewFile()) pending = marker;
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", marker, e.toString());
        }
        return false;
    }

    public static void finish() {
        if (pending == null) return;
        delete(pending);
        pending = null;
    }

    public static boolean tripped() {
        return tripped;
    }

    private static void delete(File marker) {
        if (marker.isFile() && !marker.delete()) {
            LOGGER.warn("Could not delete {}", marker);
        }
    }
}
