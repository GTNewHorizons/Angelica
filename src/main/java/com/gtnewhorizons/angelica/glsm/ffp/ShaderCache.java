package com.gtnewhorizons.angelica.glsm.ffp;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;


/**
 * Open-addressed cache for FFP shader program variants.
 * Keyed on (vertexKey, fragmentKey) pairs stored inline as raw longs — zero allocation on cache hits.
 * Converges on Mesa's prog_cache.c: hash+memcmp over packed bytes, open addressing.
 *
 * <p>Layout per slot in {@code keys[]}: {@link #SLOT_STRIDE}=6 longs
 * <pre>
 *   [0]: vertexKeyPacked
 *   [1]: (fkLen &lt;&lt; 48) | (hash &amp; 0x0000_FFFF_FFFF_FFFF)  — never 0 for valid entries (fkLen &gt;= 1)
 *   [2..5]: fk0..fk3
 * </pre>
 */
public class ShaderCache {
    private static final Logger LOGGER = LogManager.getLogger("FFPShaderCache");
    private static final int SLOT_STRIDE = 6;
    private static final int INITIAL_CAPACITY = 64; // power of 2
    private static final long EMPTY_MARKER = 0L; // slot [1] == 0 means empty (fkLen >= 1 guarantees nonzero)

    private final Long2ObjectOpenHashMap<String> vertexSourceCache = new Long2ObjectOpenHashMap<>();

    // Open-addressed table — parallel arrays
    private long[] keys;
    private Program[] programs;
    private String[] fragSources; // fragment source per slot; reused across VK variants via findFragSource
    private int capacity;
    private int count;
    private int uniqueFragCount;

    // Fast-path: last-used key (raw longs, no objects)
    private long lastVK = Long.MIN_VALUE;
    private int lastFKLen;
    private long lastFK0, lastFK1, lastFK2, lastFK3;
    private Program lastProgram;

    private int fragmentDumpCounter;

    @Setter private Path dumpDir = null;

    public ShaderCache() {
        capacity = INITIAL_CAPACITY;
        keys = new long[capacity * SLOT_STRIDE];
        programs = new Program[capacity];
        fragSources = new String[capacity];
    }

    /**
     * Get or create an FFP program for the given state keys.
     *
     * @param vkPacked packed vertex key
     * @param fkPacked pre-allocated scratch array with packed fragment key longs
     * @param fkLen number of significant longs in fkPacked (1..4)
     */
    public Program getOrCreate(long vkPacked, long[] fkPacked, int fkLen) {
        // Fast-path: compare raw longs inline
        if (vkPacked == lastVK && fkLen == lastFKLen && lastProgram != null
            && fkPacked[0] == lastFK0
            && (fkLen < 2 || fkPacked[1] == lastFK1)
            && (fkLen < 3 || fkPacked[2] == lastFK2)
            && (fkLen < 4 || fkPacked[3] == lastFK3)) {
            return lastProgram;
        }

        final long hash = hashKey(vkPacked, fkPacked, fkLen);
        final long storedWord = ((long) fkLen << 48) | (hash & 0x0000_FFFF_FFFF_FFFFL);
        final int mask = capacity - 1;
        int idx = (int) (hash & mask);
        while (true) {
            final int base = idx * SLOT_STRIDE;
            final long slot1 = keys[base + 1];
            if (slot1 == EMPTY_MARKER) {
                break;
            }
            if (slot1 == storedWord && keys[base] == vkPacked && keysMatch(base + 2, fkPacked, fkLen)) {
                final Program p = programs[idx];
                setLastUsed(vkPacked, fkPacked, fkLen, p);
                return p;
            }
            idx = (idx + 1) & mask;
        }

        if (count >= (capacity * 3) / 4) {
            grow();
            return getOrCreate(vkPacked, fkPacked, fkLen);
        }

        final VertexKey vk = VertexKey.fromPacked(vkPacked);
        final String vertSrc = getOrGenerateVertex(vk);

        final FragmentKey fk = FragmentKey.fromPacked(fkPacked, fkLen);
        String fragSrc = findFragSource(fkPacked, fkLen);
        if (fragSrc == null) {
            fragSrc = FragmentShaderGenerator.generate(fk);
            uniqueFragCount++;
        }

        final Program program = Program.create(vk, fk, vertSrc, fragSrc);

        final int insertIdx = findEmptySlot(hash);
        final int base = insertIdx * SLOT_STRIDE;
        keys[base] = vkPacked;
        keys[base + 1] = storedWord;
        for (int i = 0; i < fkLen; i++) keys[base + 2 + i] = fkPacked[i];
        programs[insertIdx] = program;
        fragSources[insertIdx] = fragSrc;
        count++;

        setLastUsed(vkPacked, fkPacked, fkLen, program);

        LOGGER.debug("FFP variant compiled: {} + {} (total: {} programs)", vk, fk, count);

        if (dumpDir != null) {
            dumpShader(Long.toHexString(vkPacked), ".vert.glsl", vertSrc);
            dumpShader("frag_" + fragmentDumpCounter++, ".frag.glsl", fragSrc);
        }

        return program;
    }

    private String findFragSource(long[] fkPacked, int fkLen) {
        for (int i = 0; i < capacity; i++) {
            final int base = i * SLOT_STRIDE;
            if (keys[base + 1] == EMPTY_MARKER) continue;
            final int storedLen = (int) (keys[base + 1] >>> 48);
            if (storedLen != fkLen) continue;
            if (keysMatch(base + 2, fkPacked, fkLen)) {
                return fragSources[i];
            }
        }
        return null;
    }

    private boolean keysMatch(int keysOffset, long[] fkPacked, int fkLen) {
        for (int i = 0; i < fkLen; i++) {
            if (keys[keysOffset + i] != fkPacked[i]) return false;
        }
        return true;
    }

    private void setLastUsed(long vkPacked, long[] fkPacked, int fkLen, Program program) {
        lastVK = vkPacked;
        lastFKLen = fkLen;
        lastFK0 = fkPacked[0];
        lastFK1 = fkLen >= 2 ? fkPacked[1] : 0;
        lastFK2 = fkLen >= 3 ? fkPacked[2] : 0;
        lastFK3 = fkLen >= 4 ? fkPacked[3] : 0;
        lastProgram = program;
    }

    private int findEmptySlot(long hash) {
        final int mask = capacity - 1;
        int idx = (int) (hash & mask);
        while (keys[idx * SLOT_STRIDE + 1] != EMPTY_MARKER) {
            idx = (idx + 1) & mask;
        }
        return idx;
    }

    /**
     * Hash over (vk + fk longs). Adapted from Mesa's hash_key — mix-rotate-multiply.
     */
    private static long hashKey(long vkPacked, long[] fkPacked, int fkLen) {
        long h = vkPacked * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < fkLen; i++) {
            h ^= fkPacked[i] * 0x517CC1B727220A95L;
            h = Long.rotateLeft(h, 31) * 0x9E3779B97F4A7C15L;
        }
        return h | 1L; // ensure nonzero lower bits
    }

    private void grow() {
        final int oldCap = capacity;
        final long[] oldKeys = keys;
        final Program[] oldPrograms = programs;
        final String[] oldFragSources = fragSources;

        capacity = oldCap * 2;
        keys = new long[capacity * SLOT_STRIDE];
        programs = new Program[capacity];
        fragSources = new String[capacity];
        count = 0;

        final int mask = capacity - 1;
        for (int i = 0; i < oldCap; i++) {
            final int oldBase = i * SLOT_STRIDE;
            if (oldKeys[oldBase + 1] == EMPTY_MARKER) continue;

            final long storedHash = oldKeys[oldBase + 1] & 0x0000_FFFF_FFFF_FFFFL;
            int idx = (int) (storedHash & mask);
            while (keys[idx * SLOT_STRIDE + 1] != EMPTY_MARKER) {
                idx = (idx + 1) & mask;
            }
            final int newBase = idx * SLOT_STRIDE;
            System.arraycopy(oldKeys, oldBase, keys, newBase, SLOT_STRIDE);
            programs[idx] = oldPrograms[i];
            fragSources[idx] = oldFragSources[i];
            count++;
        }
    }

    private String getOrGenerateVertex(VertexKey vk) {
        return vertexSourceCache.computeIfAbsent(vk.pack(), k -> VertexShaderGenerator.generate(vk));
    }

    private void dumpShader(String name, String suffix, String source) {
        if (dumpDir == null) return;
        try {
            Files.createDirectories(dumpDir);
            final Path file = dumpDir.resolve("ffp_" + name + suffix);
            if (!Files.exists(file)) {
                Files.writeString(file, source, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to dump FFP shader to {}: {}", dumpDir, e.getMessage());
        }
    }

    /** Dump all cached shaders to disk. */
    public void dumpAll(Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            for (var entry : vertexSourceCache.entrySet()) {
                dumpShader(Long.toHexString(entry.getKey()), ".vert.glsl", entry.getValue());
            }
            final IdentityHashMap<String, Integer> seen = new IdentityHashMap<>();
            for (int i = 0; i < capacity; i++) {
                if (fragSources[i] != null && !seen.containsKey(fragSources[i])) {
                    final int idx = seen.size();
                    seen.put(fragSources[i], idx);
                    dumpShader("frag_" + idx, ".frag.glsl", fragSources[i]);
                }
            }
            LOGGER.info("Dumped {} vertex and {} fragment FFP shader sources to {}",
                vertexSourceCache.size(), seen.size(), outputDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to dump FFP shaders: {}", e.getMessage());
        }
    }

    /** Destroy all cached programs and clear caches. */
    public void destroy() {
        for (int i = 0; i < capacity; i++) {
            if (programs[i] != null) {
                programs[i].destroy();
            }
        }
        Arrays.fill(keys, 0);
        Arrays.fill(programs, null);
        Arrays.fill(fragSources, null);
        count = 0;
        uniqueFragCount = 0;
        vertexSourceCache.clear();
        lastProgram = null;
        lastVK = Long.MIN_VALUE;
        LOGGER.debug("FFP shader cache cleared");
    }

    public int getProgramCount() {
        return count;
    }

    public int getVertexVariantCount() {
        return vertexSourceCache.size();
    }

    public int getFragmentVariantCount() {
        return uniqueFragCount;
    }
}
