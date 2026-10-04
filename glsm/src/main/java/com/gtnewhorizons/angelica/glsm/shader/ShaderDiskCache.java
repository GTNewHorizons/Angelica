package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.threading.AngelicaWorkers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.readBytes;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.readCount;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.readStr;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.writeBytes;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.writeStr;

public final class ShaderDiskCache {
    private static final Logger LOGGER = LogManager.getLogger("ShaderDiskCache");
    private static final int MAGIC = 0x41534331;
    private static final int HEADER_BYTES = 12;
    static final long MAX_BYTES = 256L << 20;
    private static volatile Path root;
    private static final AtomicBoolean writeWarned = new AtomicBoolean();
    private static final AtomicInteger TMP_SEQ = new AtomicInteger();

    private ShaderDiskCache() {}

    private static String configuredSalt;

    public static synchronized void configure(Path dir, String salt) {
        if (dir.equals(root) && salt.equals(configuredSalt)) return;
        try {
            Files.createDirectories(dir);
            final Path saltFile = dir.resolve("salt");
            final String existing = Files.isRegularFile(saltFile) ? new String(Files.readAllBytes(saltFile), StandardCharsets.UTF_8) : null;
            if (!salt.equals(existing)) {
                wipe(dir);
                Files.write(saltFile, salt.getBytes(StandardCharsets.UTF_8));
            }
            root = dir;
            configuredSalt = salt;
            AngelicaWorkers.run(() -> enforceCap(dir, MAX_BYTES));
            LOGGER.info("Shader disk cache at {}", dir);
        } catch (IOException | RuntimeException e) {
            root = null;
            LOGGER.warn("Shader disk cache disabled", e);
        }
    }

    public static boolean isEnabled() {
        return root != null;
    }

    public static Key key(String layer) {
        return new Key(layer);
    }

    public static byte[] get(Key key) {
        final Path r = root;
        if (r == null) return null;
        final Path file = key.path(r);
        final byte[] payload;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            payload = read(ch);
        } catch (IOException e) {
            return null;
        }
        if (payload == null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {}
        }
        return payload;
    }

    private static byte[] read(FileChannel ch) throws IOException {
        final long size = ch.size();
        if (size < HEADER_BYTES || size - HEADER_BYTES > Integer.MAX_VALUE) return null;
        final ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        readFully(ch, header);
        header.flip();
        if (header.getInt() != MAGIC) return null;
        final int length = header.getInt();
        final int crc = header.getInt();
        if (length != size - HEADER_BYTES) return null;
        final byte[] payload = new byte[length];
        readFully(ch, ByteBuffer.wrap(payload));
        final CRC32 c = new CRC32();
        c.update(payload, 0, length);
        return (int) c.getValue() == crc ? payload : null;
    }

    private static void readFully(FileChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) throw new EOFException();
        }
    }

    public static boolean put(Key key, byte[] payload) {
        final Path r = root;
        if (r == null) return false;
        final Path target = key.path(r);
        final Path tmp = target.resolveSibling(key.hex() + '.' + TMP_SEQ.getAndIncrement() + ".tmp");
        try {
            try {
                write(tmp, payload);
            } catch (IOException e) {
                Files.createDirectories(target.getParent());
                write(tmp, payload);
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {}
            if (writeWarned.compareAndSet(false, true)) LOGGER.warn("Shader disk cache write failed for {}", target, e);
            return false;
        }
    }

    private static void write(Path tmp, byte[] payload) throws IOException {
        final CRC32 c = new CRC32();
        c.update(payload, 0, payload.length);
        final ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        header.putInt(MAGIC).putInt(payload.length).putInt((int) c.getValue()).flip();
        final ByteBuffer[] parts = { header, ByteBuffer.wrap(payload) };
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (header.hasRemaining() || parts[1].hasRemaining()) ch.write(parts);
        }
    }

    public static String getString(Key key) {
        final byte[] payload = get(key);
        return payload == null ? null : new String(payload, StandardCharsets.UTF_8);
    }

    public static void putString(Key key, String value) {
        put(key, value.getBytes(StandardCharsets.UTF_8));
    }

    public static Map<String, String> getStrings(Key key) {
        final byte[] payload = get(key);
        if (payload == null) return null;
        try {
            final ByteBuffer in = ByteBuffer.wrap(payload);
            final int count = readCount(in);
            final Map<String, String> out = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                out.put(readStr(in), readStr(in));
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void putStrings(Key key, Map<String, String> values) {
        int size = 0;
        for (String v : values.values()) size += (v == null ? 0 : v.length()) + 64;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(size);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(values.size());
            for (Map.Entry<String, String> e : values.entrySet()) {
                writeStr(out, e.getKey());
                writeStr(out, e.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        put(key, bytes.toByteArray());
    }

    public static Blob getBlob(Key key) {
        final byte[] payload = get(key);
        if (payload == null) return null;
        try {
            final ByteBuffer in = ByteBuffer.wrap(payload);
            final String tag = readStr(in);
            final byte[] data = readBytes(in);
            return new Blob(tag, data);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean putBlob(Key key, String tag, byte[] data) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(data.length + 64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeStr(out, tag);
            writeBytes(out, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return put(key, bytes.toByteArray());
    }

    public record Blob(String tag, byte[] data) {}

    public static void remove(Key key) {
        final Path r = root;
        if (r == null) return;
        try {
            Files.deleteIfExists(key.path(r));
        } catch (IOException ignored) {}
    }

    public static void retainLayer(String layer, Set<String> keep) {
        final Path r = root;
        if (r == null) return;
        final Path dir = r.resolve(layer);
        if (!Files.isDirectory(dir)) return;
        final List<Path> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
            list.forEach(files::add);
        } catch (IOException e) {
            LOGGER.warn("Shader disk cache could not list {}", dir, e);
            return;
        }
        for (Path p : files) {
            final String name = p.getFileName().toString();
            if (!name.endsWith(".bin")) continue; // in-flight writes are still .tmp
            if (keep != null && keep.contains(name.substring(0, name.length() - 4))) continue;
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {}
        }
    }

    public static void touchLayer(String layer) {
        final Path r = root;
        if (r == null) return;
        final Path dir = r.resolve(layer);
        try {
            Files.createDirectories(dir);
            Files.setLastModifiedTime(dir, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {
            LOGGER.warn("Shader disk cache could not mark {} as used", dir, e);
        }
    }

    public static void keepNewestSubLayers(String layer, int keep) {
        final Path r = root;
        if (r == null) return;
        final Path dir = r.resolve(layer);
        if (!Files.isDirectory(dir)) return;
        final List<Path> subLayers = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(Files::isDirectory).forEach(subLayers::add);
        } catch (IOException e) {
            LOGGER.warn("Shader disk cache could not list {}", dir, e);
            return;
        }
        subLayers.sort(Comparator.comparingLong(ShaderDiskCache::lastModifiedMillis).reversed());
        for (int i = keep; i < subLayers.size(); i++) {
            deleteRecursively(subLayers.get(i));
        }
    }

    public static void deleteLayer(String layer) {
        final Path r = root;
        if (r != null) deleteRecursively(r.resolve(layer));
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        final List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.forEach(paths::add);
        } catch (IOException e) {
            LOGGER.warn("Shader disk cache could not list {}", dir, e);
            return;
        }
        Collections.reverse(paths);
        for (Path p : paths) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {}
        }
    }

    static void enforceCap(Path dir, long maxBytes) {
        try {
            final long[] total = {0};
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path p, BasicFileAttributes a) { total[0] += a.size(); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult visitFileFailed(Path p, IOException e) { return FileVisitResult.CONTINUE; }
            });
            if (total[0] > maxBytes) wipe(dir);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Shader disk cache cap check failed", e);
        }
    }

    private static void wipe(Path dir) throws IOException {
        final List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.forEach(paths::add);
        }
        for (Path p : paths) {
            if (Files.isRegularFile(p) && !p.getFileName().toString().equals("salt")) Files.deleteIfExists(p);
        }
    }

    public static final class Key {
        private final String layer;
        private final MessageDigest md;
        private String hex;

        Key(String layer) {
            this.layer = layer;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            str(layer);
        }

        public Key str(String s) {
            if (s == null) return i(-1);
            final byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            i(utf8.length);
            md.update(utf8);
            return this;
        }

        public Key i(int v) {
            md.update((byte) (v >>> 24));
            md.update((byte) (v >>> 16));
            md.update((byte) (v >>> 8));
            md.update((byte) v);
            return this;
        }

        public Key b(boolean v) {
            md.update((byte) (v ? 1 : 0));
            return this;
        }

        public Key bytes(byte[] v) {
            i(v.length);
            md.update(v);
            return this;
        }

        public Key sortedStrs(Collection<String> values) {
            final String[] sorted = values.toArray(new String[0]);
            Arrays.sort(sorted);
            i(sorted.length);
            for (String s : sorted) str(s);
            return this;
        }

        public String hex() {
            if (hex == null) {
                final byte[] digest = md.digest();
                final StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte d : digest) {
                    sb.append(Character.forDigit((d >>> 4) & 0xF, 16)).append(Character.forDigit(d & 0xF, 16));
                }
                hex = sb.toString();
            }
            return hex;
        }

        Path path(Path root) {
            return root.resolve(layer).resolve(hex() + ".bin");
        }
    }
}
