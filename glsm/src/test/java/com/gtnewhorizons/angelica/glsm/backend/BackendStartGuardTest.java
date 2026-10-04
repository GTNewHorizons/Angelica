package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendStartGuardTest {

    private static File marker(Path dir) {
        return dir.resolve("angelica").resolve("sdlgpu-starting").toFile();
    }

    @Test
    void firstStartCreatesTheMarkerAndFinishRemovesIt(@TempDir Path dir) {
        final File marker = marker(dir);
        assertFalse(BackendStartGuard.begin(marker, true, false));
        assertFalse(BackendStartGuard.tripped());
        assertTrue(marker.isFile());
        BackendStartGuard.finish();
        assertFalse(marker.exists());
    }

    @Test
    void leftoverMarkerTripsOnceAndIsRemoved(@TempDir Path dir) {
        final File marker = marker(dir);
        assertFalse(BackendStartGuard.begin(marker, true, false));
        assertTrue(BackendStartGuard.begin(marker, true, false));
        assertTrue(BackendStartGuard.tripped());
        assertFalse(marker.exists());
        BackendStartGuard.finish();
        assertFalse(marker.exists());
        assertFalse(BackendStartGuard.begin(marker, true, false));
        assertFalse(BackendStartGuard.tripped());
        BackendStartGuard.finish();
    }

    @Test
    void openGlConfigWritesNoMarkerAndClearsAStaleOne(@TempDir Path dir) throws IOException {
        final File marker = marker(dir);
        assertFalse(BackendStartGuard.begin(marker, false, false));
        assertFalse(marker.exists());
        assertTrue(marker.getParentFile().mkdirs());
        assertTrue(marker.createNewFile());
        assertFalse(BackendStartGuard.begin(marker, false, false));
        assertFalse(BackendStartGuard.tripped());
        assertFalse(marker.exists());
    }

    @Test
    void forcedFlagNeverTripsOrWrites(@TempDir Path dir) throws IOException {
        final File marker = marker(dir);
        assertFalse(BackendStartGuard.begin(marker, true, true));
        assertFalse(marker.exists());
        assertTrue(marker.getParentFile().mkdirs());
        assertTrue(marker.createNewFile());
        assertFalse(BackendStartGuard.begin(marker, true, true));
        assertFalse(BackendStartGuard.tripped());
    }
}
