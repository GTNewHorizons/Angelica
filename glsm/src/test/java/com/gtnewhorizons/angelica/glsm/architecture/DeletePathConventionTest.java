package com.gtnewhorizons.angelica.glsm.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletePathConventionTest {

    private static final Pattern FORBIDDEN = Pattern.compile("\\bRENDER_BACKEND\\s*\\.\\s*delete[A-Z][A-Za-z]*\\b");
    private static final String ALLOWLIST_MARKER = "// glsm-allow-direct-delete:";

    /** Only this file is allowed to call RENDER_BACKEND.delete* — it is the cache-invalidation layer. */
    private static final String GATEKEEPER_FILE_NAME = "GLStateManager.java";

    @Test
    void glsmMainSources_onlyCallBackendDeleteFromGLStateManager() throws IOException {
        final Path root = locateGlsmMainRoot();
        final List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals(GATEKEEPER_FILE_NAME))
                .forEach(p -> scanFile(p, violations));
        }

        assertTrue(violations.isEmpty(),
            () -> "Direct RENDER_BACKEND.delete*(...) call found outside " + GATEKEEPER_FILE_NAME + ".\n"
                + "Use GLStateManager.glDelete*() instead\n"
                + "Violations:\n  " + String.join("\n  ", violations));
    }

    private static void scanFile(Path file, List<String> violations) {
        final List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            violations.add(file + ": I/O error reading file: " + e.getMessage());
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final Matcher m = FORBIDDEN.matcher(line);
            if (!m.find()) continue;
            if (line.contains(ALLOWLIST_MARKER)) continue;
            violations.add(file + ":" + (i + 1) + "  " + line.trim());
        }
    }

    private static Path locateGlsmMainRoot() {
        final String[] candidates = {
            "src/main/java/com/gtnewhorizons/angelica/glsm",
            "glsm/src/main/java/com/gtnewhorizons/angelica/glsm",
        };
        for (String c : candidates) {
            final Path p = Paths.get(c).toAbsolutePath();
            if (Files.isDirectory(p)) return p;
        }
        throw new IllegalStateException("Could not find glsm main sources — cwd=" + Paths.get("").toAbsolutePath());
    }
}
