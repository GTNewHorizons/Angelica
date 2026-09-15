package com.gtnewhorizons.angelica.debug.flyby;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlybySceneTest {

    @TempDir
    Path tempDir;

    private String writeFile(String name, String content) throws IOException {
        final Path path = tempDir.resolve(name);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path.toString();
    }

    @Test
    void dropsCommentsAndBlankLines() throws IOException {
        final String path = writeFile("scene.txt", "# comment\n\ntime set day\n   \nweather clear\n");
        assertArrayEquals(new String[] { "time set day", "weather clear" }, FlybyScene.load(path));
    }

    @Test
    void stripsOneLeadingSlash() throws IOException {
        final String path = writeFile("scene.txt", "//foo\n/bar\n");
        assertArrayEquals(new String[] { "/foo", "bar" }, FlybyScene.load(path));
    }

    @Test
    void trimsSurroundingWhitespace() throws IOException {
        final String path = writeFile("scene.txt", "   time set day   \n");
        assertArrayEquals(new String[] { "time set day" }, FlybyScene.load(path));
    }

    @Test
    void handlesCrlfLineEndings() throws IOException {
        final Path path = tempDir.resolve("scene.txt");
        Files.write(path, "time set day\r\nweather clear\r\n".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(new String[] { "time set day", "weather clear" }, FlybyScene.load(path.toString()));
    }

    @Test
    void dropsBareSlashLine() throws IOException {
        final String path = writeFile("scene.txt", "time set day\n/\nweather clear\n");
        assertArrayEquals(new String[] { "time set day", "weather clear" }, FlybyScene.load(path));
    }

    @Test
    void preservesOrder() throws IOException {
        final String path = writeFile("scene.txt", "one\ntwo\nthree\n");
        assertArrayEquals(new String[] { "one", "two", "three" }, FlybyScene.load(path));
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
