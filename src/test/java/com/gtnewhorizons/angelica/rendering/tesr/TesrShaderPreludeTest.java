package com.gtnewhorizons.angelica.rendering.tesr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TesrShaderPreludeTest {

    private static final Pattern GLOBAL = Pattern.compile("^(?:layout\\([^)]*\\) )?(?:in|out|uniform|struct|[a-z0-9]+) (?:[A-Za-z0-9_]+ )?([A-Za-z_][A-Za-z0-9_]*)[;(\\[ ]", Pattern.MULTILINE);

    @Test
    void everyGlobalIsPrefixedAndMainIsLeftToTheBody() {
        for (final String source : new String[] { TesrShaderPrelude.vertex(false), TesrShaderPrelude.vertex(true), TesrShaderPrelude.fragment() }) {
            final Matcher matcher = GLOBAL.matcher(source);
            int globals = 0;
            while (matcher.find()) {
                globals++;
                final String name = matcher.group(1);
                assertTrue(name.startsWith("angelica_") || name.startsWith("iris_") || name.equals("gl_Position"), "unprefixed global " + name);
            }
            assertTrue(globals >= 10, "the scan must see the declarations, saw " + globals);
            assertFalse(source.contains("main"));
        }
    }

    static Stream<Arguments> versionedBodies() {
        return Stream.of(
            Arguments.of("leading directive", "#version 120\nvoid main() {}\n", "void main() {}\n"),
            Arguments.of("after whitespace", "\n  #version 330 core\nvoid main() {}\n", "void main() {}\n"),
            Arguments.of("after a line comment", "// header\n#version 330 core\nvoid main() {}\n", "void main() {}\n"),
            Arguments.of("after a block comment", "/* a\n b */ #version 330 core\nvoid main() {}\n", "void main() {}\n"),
            Arguments.of("directive only", "#version 330 core", ""),
            Arguments.of("no directive", "void main() {}\n", "void main() {}\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("versionedBodies")
    void stripVersionDropsOnlyALeadingDirective(String name, String body, String expected) {
        assertEquals(expected, TesrShaderPrelude.stripVersion("test:strip", body));
    }
}
