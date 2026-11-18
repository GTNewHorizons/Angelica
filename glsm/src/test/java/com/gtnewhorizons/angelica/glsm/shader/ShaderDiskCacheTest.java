package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderDiskCacheTest {

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        Reflect.setStatic(ShaderDiskCache.class, "root", null);
    }

    private static long binCount(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".bin")).count();
        }
    }

    @Test
    void roundTripAndDisabled() throws IOException {
        ShaderDiskCache.put(ShaderDiskCache.key("l").str("x"), new byte[] {1});
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("l").str("x")));
        assertEquals(0, binCount(dir));

        ShaderDiskCache.configure(dir, "a");
        ShaderDiskCache.put(ShaderDiskCache.key("l").str("x"), new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, ShaderDiskCache.get(ShaderDiskCache.key("l").str("x")));
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("l").str("y")));

        ShaderDiskCache.put(ShaderDiskCache.key("t").str("empty"), new byte[0]);
        assertArrayEquals(new byte[0], ShaderDiskCache.get(ShaderDiskCache.key("t").str("empty")));
    }

    @Test
    void saltChangeWipes() {
        ShaderDiskCache.configure(dir, "a");
        ShaderDiskCache.put(ShaderDiskCache.key("l").str("x"), new byte[] {7});
        ShaderDiskCache.configure(dir, "a");
        assertArrayEquals(new byte[] {7}, ShaderDiskCache.get(ShaderDiskCache.key("l").str("x")));
        ShaderDiskCache.configure(dir, "b");
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("l").str("x")));
    }

    @Test
    void corruptAndTruncatedAreMisses() throws IOException {
        ShaderDiskCache.configure(dir, "a");
        final ShaderDiskCache.Key k = ShaderDiskCache.key("l").str("x");
        ShaderDiskCache.put(k, new byte[] {1, 2, 3, 4});
        final Path file = k.path(dir);
        final byte[] raw = Files.readAllBytes(file);
        raw[raw.length - 1] ^= 1;
        Files.write(file, raw);
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("l").str("x")));
        assertFalse(Files.exists(file));

        ShaderDiskCache.put(ShaderDiskCache.key("l").str("x"), new byte[] {1, 2, 3, 4});
        final byte[] full = Files.readAllBytes(file);
        final byte[] cut = new byte[full.length - 2];
        System.arraycopy(full, 0, cut, 0, cut.length);
        Files.write(file, cut);
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("l").str("x")));

        final ShaderDiskCache.Key shortKey = ShaderDiskCache.key("t").str("short");
        ShaderDiskCache.put(shortKey, new byte[] {1});
        final Path shortFile = shortKey.path(dir);
        Files.write(shortFile, new byte[] {0x41, 0x53});
        assertNull(ShaderDiskCache.get(ShaderDiskCache.key("t").str("short")));
        assertFalse(Files.exists(shortFile));
    }

    @Test
    void keysAreFramed() {
        assertNotEquals(ShaderDiskCache.key("l").str("ab").str("c").hex(), ShaderDiskCache.key("l").str("a").str("bc").hex());
        assertNotEquals(ShaderDiskCache.key("l").str(null).hex(), ShaderDiskCache.key("l").str("").hex());
    }

    @Test
    void stringsAndBlobs() {
        ShaderDiskCache.configure(dir, "a");
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("VERTEX", null);
        values.put("FRAGMENT", "x".repeat(100000));
        ShaderDiskCache.putStrings(ShaderDiskCache.key("s").str("1"), values);
        assertEquals(values, ShaderDiskCache.getStrings(ShaderDiskCache.key("s").str("1")));

        ShaderDiskCache.putBlob(ShaderDiskCache.key("b").str("1"), "main0", new byte[] {9, 8, 7});
        final ShaderDiskCache.Blob blob = ShaderDiskCache.getBlob(ShaderDiskCache.key("b").str("1"));
        assertNotNull(blob);
        assertEquals("main0", blob.tag());
        assertArrayEquals(new byte[] {9, 8, 7}, blob.data());
    }

    @Test
    void overCapWipesEntriesAndKeepsSalt() throws IOException {
        ShaderDiskCache.configure(dir, "a");
        for (int i = 0; i < 4; i++) ShaderDiskCache.put(ShaderDiskCache.key("t").i(i), new byte[1000]);
        ShaderDiskCache.enforceCap(dir, 1 << 20);
        assertEquals(4, binCount(dir));
        ShaderDiskCache.enforceCap(dir, 3000);
        assertEquals(0, binCount(dir));
        assertTrue(Files.isRegularFile(dir.resolve("salt")));
        ShaderDiskCache.put(ShaderDiskCache.key("t").i(9), new byte[] {5});
        assertArrayEquals(new byte[] {5}, ShaderDiskCache.get(ShaderDiskCache.key("t").i(9)));
    }

    @Test
    void retainLayerKeepsListedEntriesOtherLayersAndInFlightWrites() throws IOException {
        ShaderDiskCache.configure(dir, "a");
        final ShaderDiskCache.Key kept = ShaderDiskCache.key("p").str("kept");
        final ShaderDiskCache.Key dropped = ShaderDiskCache.key("p").str("dropped");
        final ShaderDiskCache.Key otherLayer = ShaderDiskCache.key("q").str("dropped");
        ShaderDiskCache.put(kept, new byte[] {1});
        ShaderDiskCache.put(dropped, new byte[] {2});
        ShaderDiskCache.put(otherLayer, new byte[] {3});
        final Path inFlight = dir.resolve("p").resolve(dropped.hex() + ".7.tmp");
        Files.write(inFlight, new byte[] {4});

        ShaderDiskCache.retainLayer("p", Set.of(kept.hex()));
        assertArrayEquals(new byte[] {1}, ShaderDiskCache.get(kept));
        assertNull(ShaderDiskCache.get(dropped));
        assertArrayEquals(new byte[] {3}, ShaderDiskCache.get(otherLayer));
        assertTrue(Files.exists(inFlight));

        ShaderDiskCache.retainLayer("p", null);
        assertNull(ShaderDiskCache.get(kept));
        assertArrayEquals(new byte[] {3}, ShaderDiskCache.get(otherLayer));
    }
}
