package com.gtnewhorizons.angelica.debug.flyby;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlybySceneTest {

    @TempDir
    Path tempDir;

    private String writeFile(String name, String content) throws IOException {
        final Path path = tempDir.resolve(name);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path.toString();
    }

    static Stream<Arguments> sceneFiles() {
        return Stream.of(
            Arguments.of("drops comments and blank lines", "# comment\n\ntime set day\n   \nweather clear\n", new String[] { "time set day", "weather clear" }),
            Arguments.of("strips one leading slash", "//foo\n/bar\n", new String[] { "/foo", "bar" }),
            Arguments.of("trims surrounding whitespace", "   time set day   \n", new String[] { "time set day" }),
            Arguments.of("handles CRLF line endings", "time set day\r\nweather clear\r\n", new String[] { "time set day", "weather clear" }),
            Arguments.of("drops a bare slash line", "time set day\n/\nweather clear\n", new String[] { "time set day", "weather clear" }),
            Arguments.of("preserves order", "one\ntwo\nthree\n", new String[] { "one", "two", "three" }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sceneFiles")
    void loadsCommands(String name, String content, String[] expected) throws IOException {
        assertArrayEquals(expected, FlybyScene.load(writeFile("scene.txt", content)));
    }

    @Test
    void expandsCoordinateTokens() {
        assertEquals("setblock 94 63 -48 stone {TileX:100}", FlybyScene.expand("setblock $x-6 $y-1 $z+2 stone {TileX:$x}", 100, 64, -50));
        final String plain = "setblock ~ ~-1 ~ stone";
        assertSame(plain, FlybyScene.expand(plain, 100, 64, -50));
    }

    @Test
    void missingFileReturnsEmptyArray() {
        final String path = tempDir.resolve("does-not-exist.txt").toString();
        assertEquals(0, FlybyScene.load(path).length);
    }

    @Test
    void shippedScenesTagEverythingTheyPlace() {
        for (String path : new String[] { "scripts/flyby-scenes/entities.txt", "scripts/flyby-scenes/entities-x8.txt" }) {
            assertTrue(Files.isRegularFile(Paths.get(path)), path);
            final String[] commands = FlybyScene.load(path);
            assertTrue(commands.length > 0, path);
            for (String command : commands) {
                assertFalse(command.contains("netherrack"), command);
                if (command.startsWith("setblock ") && !command.contains("minecraft:fire")) {
                    assertTrue(command.contains("minecraft:dispenser 0 replace {CustomName:\"flyby\"}"), command);
                }
                if (command.startsWith("summon ")) {
                    assertTrue(command.contains("ForgeData:{flyby:1b}"), command);
                }
            }
        }
    }
}
