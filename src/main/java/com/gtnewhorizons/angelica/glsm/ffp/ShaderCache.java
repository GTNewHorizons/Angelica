package com.gtnewhorizons.angelica.glsm.ffp;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cache for FFP shader program variants. Keyed on (vertexKey, fragmentKey) pairs.
 */
public class ShaderCache {
    private static final Logger LOGGER = LogManager.getLogger("FFPShaderCache");

    private final Long2ObjectOpenHashMap<String> vertexSourceCache = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<String> fragmentSourceCache = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Program> programCache = new Long2ObjectOpenHashMap<>();

    private long lastCombinedKey = Long.MIN_VALUE;
    private Program lastProgram = null;

    @Setter private Path dumpDir = null;

    /**
     * Get or create an FFP program for the given packed state keys.
     */
    public Program getOrCreate(long vkPacked, long fkPacked) {
        final long combinedKey = (fkPacked << 32) | (vkPacked & 0xFFFFFFFFL);

        if (combinedKey == lastCombinedKey && lastProgram != null) {
            return lastProgram;
        }

        Program program = programCache.get(combinedKey);
        if (program != null) {
            lastCombinedKey = combinedKey;
            lastProgram = program;

            return program;
        }

        final VertexKey vk = VertexKey.fromPacked(vkPacked);
        final FragmentKey fk = FragmentKey.fromPacked(fkPacked);

        final String vertSrc = getOrGenerateVertex(vk);
        final String fragSrc = getOrGenerateFragment(fk);

        program = Program.create(vk, fk, vertSrc, fragSrc);
        programCache.put(combinedKey, program);
        lastCombinedKey = combinedKey;
        lastProgram = program;

        LOGGER.debug("FFP variant compiled: {} + {} (total: {} programs)", vk, fk, programCache.size());

        // Debug dump
        if (dumpDir != null) {
            dumpShader(vkPacked, ".vert.glsl", vertSrc);
            dumpShader(fkPacked, ".frag.glsl", fragSrc);
        }

        return program;
    }

    private String getOrGenerateVertex(VertexKey vk) {
        return vertexSourceCache.computeIfAbsent(vk.pack(), k -> VertexShaderGenerator.generate(vk));
    }

    private String getOrGenerateFragment(FragmentKey fk) {
        return fragmentSourceCache.computeIfAbsent(fk.pack(), k -> FragmentShaderGenerator.generate(fk));
    }

    private void dumpShader(long key, String suffix, String source) {
        if (dumpDir == null) return;
        try {
            Files.createDirectories(dumpDir);
            final Path file = dumpDir.resolve("ffp_" + Long.toHexString(key) + suffix);
            if (!Files.exists(file)) {
                Files.writeString(file, source, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to dump FFP shader to {}: {}", dumpDir, e.getMessage());
        }
    }

    /**
     * Dump all cached shaders to disk. Useful for debugging.
     */
    public void dumpAll(Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            for (var entry : vertexSourceCache.entrySet()) {
                dumpShader(entry.getKey(), ".vert.glsl", entry.getValue());
            }
            for (var entry : fragmentSourceCache.entrySet()) {
                dumpShader(entry.getKey(), ".frag.glsl", entry.getValue());
            }
            LOGGER.info("Dumped {} vertex and {} fragment FFP shader sources to {}", vertexSourceCache.size(), fragmentSourceCache.size(), outputDir);
        } catch (IOException e) {
            LOGGER.warn("Failed to dump FFP shaders: {}", e.getMessage());
        }
    }

    /**
     * Destroy all cached programs and clear caches.
     */
    public void destroy() {
        for (Program program : programCache.values()) {
            program.destroy();
        }
        programCache.clear();
        vertexSourceCache.clear();
        fragmentSourceCache.clear();
        lastProgram = null;
        lastCombinedKey = Long.MIN_VALUE;
        LOGGER.debug("FFP shader cache cleared");
    }

    public int getProgramCount() {
        return programCache.size();
    }

    public int getVertexVariantCount() {
        return vertexSourceCache.size();
    }

    public int getFragmentVariantCount() {
        return fragmentSourceCache.size();
    }
}
