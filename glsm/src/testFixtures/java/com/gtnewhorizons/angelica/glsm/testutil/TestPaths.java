package com.gtnewhorizons.angelica.glsm.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class TestPaths {

    private TestPaths() {}

    public static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not find repo root from " + Paths.get("").toAbsolutePath());
    }

    public static Path resolve(String repoRelative) {
        return repoRoot().resolve(repoRelative);
    }

    public static String readString(String repoRelative) {
        final Path p = resolve(repoRelative);
        if (!Files.isRegularFile(p)) throw new IllegalStateException("missing file: " + p);
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
