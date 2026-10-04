package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoltenVKTest {

    private static final String PRESENT_RESOURCE = "/com/gtnewhorizons/angelica/glsm/backend/MoltenVK.class";
    private static final String ABSENT_RESOURCE = "/macos/arm64/org/lwjgl/vulkan/absent.dylib";

    @Test
    void detectOrderIsEnvThenJarThenSystem(@TempDir Path dir) throws IOException {
        final String[] system = { Files.createFile(dir.resolve("system.dylib")).toString() };
        final String[] none = { dir.resolve("missing.dylib").toString() };
        assertEquals(MoltenVK.Source.NONE, MoltenVK.detect(false, "/env/lib.dylib", true, system));
        assertEquals(MoltenVK.Source.ENV, MoltenVK.detect(true, "/env/lib.dylib", true, system));
        assertEquals(MoltenVK.Source.JAR, MoltenVK.detect(true, "", true, system));
        assertEquals(MoltenVK.Source.SYSTEM, MoltenVK.detect(true, null, false, system));
        assertEquals(MoltenVK.Source.NONE, MoltenVK.detect(true, null, false, none));
        assertEquals(1, dir.toFile().list().length);
    }

    @Test
    void jarIsExtractedOnceAndNotRewritten(@TempDir Path dir) throws IOException {
        final File out = dir.resolve("natives").toFile();
        final String first = MoltenVK.resolve(MoltenVK.Source.JAR, null, PRESENT_RESOURCE, out, "arm64", new String[0]);
        final File extracted = new File(out, "libMoltenVK-arm64.dylib");
        assertEquals(extracted.getAbsolutePath(), first);
        assertTrue(extracted.length() > 0);
        assertTrue(extracted.setLastModified(1_000_000L));
        assertEquals(first, MoltenVK.resolve(MoltenVK.Source.JAR, null, PRESENT_RESOURCE, out, "arm64", new String[0]));
        assertEquals(1_000_000L, extracted.lastModified());
        assertEquals(1, out.list().length);
    }

    @Test
    void changedFileIsRewritten(@TempDir Path dir) throws IOException {
        final File extracted = new File(dir.toFile(), "libMoltenVK-arm64.dylib");
        Files.write(extracted.toPath(), new byte[] { 1, 2, 3 });
        MoltenVK.resolve(MoltenVK.Source.JAR, null, PRESENT_RESOURCE, dir.toFile(), "arm64", new String[0]);
        assertTrue(extracted.length() > 3);
    }

    @Test
    void failedExtractionFallsBackToTheSystemInstall(@TempDir Path dir) throws IOException {
        final Path system = Files.createFile(dir.resolve("system.dylib"));
        final File notADirectory = Files.createFile(dir.resolve("blocker")).toFile();
        assertEquals(system.toString(), MoltenVK.resolve(MoltenVK.Source.JAR, null, PRESENT_RESOURCE, notADirectory, "arm64", new String[] { system.toString() }));
        assertNull(MoltenVK.resolve(MoltenVK.Source.JAR, null, ABSENT_RESOURCE, dir.toFile(), "arm64", new String[0]));
    }

    @Test
    void envAndSystemAndNoneResolveWithoutExtracting(@TempDir Path dir) throws IOException {
        final Path system = Files.createFile(dir.resolve("system.dylib"));
        final File out = dir.resolve("natives").toFile();
        assertEquals("/env/lib.dylib", MoltenVK.resolve(MoltenVK.Source.ENV, "/env/lib.dylib", PRESENT_RESOURCE, out, "arm64", new String[0]));
        assertEquals(system.toString(), MoltenVK.resolve(MoltenVK.Source.SYSTEM, null, PRESENT_RESOURCE, out, "arm64", new String[] { system.toString() }));
        assertNull(MoltenVK.resolve(MoltenVK.Source.NONE, null, PRESENT_RESOURCE, out, "arm64", new String[] { system.toString() }));
        assertFalse(out.exists());
    }

    @Test
    void archNames() {
        assertEquals("arm64", MoltenVK.archName("aarch64"));
        assertEquals("x64", MoltenVK.archName("x86_64"));
    }
}
