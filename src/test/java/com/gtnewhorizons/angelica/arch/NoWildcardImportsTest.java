package com.gtnewhorizons.angelica.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoWildcardImportsTest {

    private static final List<String> SOURCE_DIRS = List.of("src/main/java", "src/mixin/java", "src/test/java");

    private static final Pattern WILDCARD_IMPORT = Pattern.compile("\\s*import\\s+(?!static\\s)[\\w.]+\\.\\*\\s*;.*");

    @Test
    void noWildcardImports() throws IOException {
        final List<String> violations = new ArrayList<>();
        for (String dir : SOURCE_DIRS) {
            final List<Path> sources;
            try (Stream<Path> files = Files.walk(Paths.get(dir))) {
                sources = files.filter(file -> file.toString().endsWith(".java")).collect(Collectors.toList());
            }
            assertFalse(sources.isEmpty(), dir + " has no Java sources");
            for (Path source : sources) {
                final String[] lines = new String(Files.readAllBytes(source), StandardCharsets.ISO_8859_1).split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (WILDCARD_IMPORT.matcher(lines[i]).matches()) violations.add("  " + source + ":" + (i + 1));
                }
            }
        }
        assertTrue(violations.isEmpty(), "Wildcard imports (static ones are allowed):\n" + String.join("\n", violations));
    }
}
