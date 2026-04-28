package com.gtnewhorizons.angelica.glsm.shader;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memUTF8;

/**
 * GLSL -> SPIR-V via shaderc, Vulkan target. Stateless; ThreadLocal compiler enables
 * parallel compilation. On failure, writes the source + error to {@code shaderc_failures/}
 * so the post-preprocess text that tripped shaderc is recoverable (Iris-transformed
 * shaders routinely exceed 100KB and reference line numbers that don't exist in the
 * pre-preprocess source).
 */
@Lwjgl3Aware
public final class SpirvCompiler {

    private static final Logger LOGGER = LogManager.getLogger("SpirvCompiler");

    private static final ThreadLocal<Long> COMPILER = ThreadLocal.withInitial(() -> {
        final long c = Shaderc.shaderc_compiler_initialize();
        if (c == 0L) throw new IllegalStateException("shaderc_compiler_initialize failed");
        return c;
    });

    private static final Path FAILURE_DIR = Paths.get("shaderc_failures");
    private static final AtomicInteger FAILURE_COUNTER = new AtomicInteger();

    private SpirvCompiler() {}

    /** Tunable options; factories capture per-caller shaderc setup. */
    public record Options(int targetEnv, int envVersion, boolean vulkanRulesRelaxed,
                          boolean autoBindUniforms, boolean autoMapLocations,
                          boolean autoCombinedImageSampler,
                          int forcedVersion, int forcedProfile) {

        /** gles translator: accept loose uniforms, auto-map everything, no forced version. */
        public static Options vulkanRelaxed() {
            return new Options(Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_0, true, true, true, true, 0, 0);
        }

        /** SDL backend: force GLSL 460 core and otherwise match vulkanRelaxed. */
        public static Options vulkanForced460Core() {
            return new Options(Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_0, true, true, true, true, 460, Shaderc.shaderc_profile_core);
        }
    }

    /** Compile outcome. Exactly one of {@code spirv}/{@code error} is non-null. Caller owns
     *  {@code spirv} (off-heap {@link MemoryUtil#memAlloc}) and must {@code memFree} it. */
    public record Result(@Nullable ByteBuffer spirv, @Nullable String error, @Nullable Path dumpPath) {}

    public static Result compile(String source, int shaderKind, String debugName, Options opts) {
        final long compiler = COMPILER.get();
        final long optionsHandle = Shaderc.shaderc_compile_options_initialize();
        if (optionsHandle == 0L) {
            return new Result(null, "shaderc_compile_options_initialize failed", null);
        }
        try {
            configureOptions(optionsHandle, opts);

            // shaderc parses an embedded NUL as garbage; GL callers often pass null-terminated strings.
            final String cleanSrc = source.indexOf('\0') >= 0 ? source.replace("\0", "") : source;

            // Iris-transformed shaders can exceed MemoryStack's 64KB limit; allocate on the heap.
            ByteBuffer srcBuf = null, nameBuf = null, entryBuf = null;
            long result = 0L;
            try {
                srcBuf = memUTF8(cleanSrc, false);
                nameBuf = memUTF8(debugName == null ? "shader" : debugName);
                entryBuf = memUTF8("main");
                result = Shaderc.shaderc_compile_into_spv(compiler, srcBuf, shaderKind, nameBuf, entryBuf, optionsHandle);
                if (result == 0L) {
                    return new Result(null, "shaderc_compile_into_spv returned null", null);
                }
                final int status = Shaderc.shaderc_result_get_compilation_status(result);
                if (status != Shaderc.shaderc_compilation_status_success) {
                    final String msg = Shaderc.shaderc_result_get_error_message(result);
                    final Path dumpPath = writeFailureDump(debugName, shaderKind, msg, cleanSrc);
                    return new Result(null, msg == null ? "(no error message)" : msg, dumpPath);
                }
                final ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
                if (spirv == null || spirv.remaining() == 0) {
                    return new Result(null, "shaderc returned empty SPIR-V", null);
                }
                // Copy off the shaderc-owned buffer before releasing the result.
                final ByteBuffer copy = MemoryUtil.memAlloc(spirv.remaining());
                copy.put(spirv);
                copy.flip();
                return new Result(copy, null, null);
            } finally {
                if (result != 0L) Shaderc.shaderc_result_release(result);
                if (entryBuf != null) memFree(entryBuf);
                if (nameBuf != null) memFree(nameBuf);
                if (srcBuf != null) memFree(srcBuf);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(optionsHandle);
        }
    }

    private static void configureOptions(long h, Options o) {
        Shaderc.shaderc_compile_options_set_target_env(h, o.targetEnv(), o.envVersion());
        Shaderc.shaderc_compile_options_set_source_language(h, Shaderc.shaderc_source_language_glsl);
        if (o.vulkanRulesRelaxed()) Shaderc.shaderc_compile_options_set_vulkan_rules_relaxed(h, true);
        if (o.autoBindUniforms()) Shaderc.shaderc_compile_options_set_auto_bind_uniforms(h, true);
        if (o.autoMapLocations()) Shaderc.shaderc_compile_options_set_auto_map_locations(h, true);
        if (o.autoCombinedImageSampler()) Shaderc.shaderc_compile_options_set_auto_combined_image_sampler(h, true);
        if (o.forcedVersion() != 0) {
            Shaderc.shaderc_compile_options_set_forced_version_profile(h, o.forcedVersion(), o.forcedProfile());
        }
    }

    private static @Nullable Path writeFailureDump(String debugName, int shaderKind, String msg, String source) {
        try {
            Files.createDirectories(FAILURE_DIR);
            final int n = FAILURE_COUNTER.getAndIncrement();
            final Path p = FAILURE_DIR.resolve(String.format("%03d_%s_kind0x%s.glsl", n, debugName == null ? "shader" : debugName, Integer.toHexString(shaderKind)));
            final StringBuilder sb = new StringBuilder();
            sb.append("// shaderc errors:\n");
            if (msg != null) for (String line : msg.split("\\R")) sb.append("// ").append(line).append('\n');
            sb.append('\n').append(source);
            Files.writeString(p, sb.toString());
            return p;
        } catch (IOException e) {
            LOGGER.warn("Failed to write shaderc failure dump for '{}': {}", debugName, e.getMessage());
            return null;
        }
    }
}
