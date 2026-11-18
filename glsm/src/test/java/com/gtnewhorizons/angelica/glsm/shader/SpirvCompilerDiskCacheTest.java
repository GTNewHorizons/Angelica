package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("lwjgl3")
class SpirvCompilerDiskCacheTest {

    private static final String SRC = "#version 460 core\nlayout(location = 0) in vec4 p;\nvoid main() { gl_Position = p; }\n";

    static final String REDEFINES_CLAMP = """
        #version 330 core
        in vec4 uv;
        layout(location = 0) out vec4 color;
        float clamp(float min, float f, float max) {
            if (f > max) f = max;
            if (f < min) f = min;
            return f;
        }
        void main() {
            color = vec4(clamp(0.0, uv.x, 1.0), clamp(0.0, uv.y, 1.0), 0.0, 1.0);
        }
        """;

    private static final String BROKEN_WITH_HELPER = """
        #version 330 core
        layout(location = 0) out vec4 color;
        float helper(float t) { return t; }
        void main() { color = vec4(helper(undeclared_var)); }
        """;

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        Reflect.setStatic(ShaderDiskCache.class, "root", null);
        SpirvCompiler.clearCache();
    }

    @Test
    void diskEntryServedAfterMemoryClear() {
        final SpirvCompiler.Options opts = SpirvCompiler.Options.vulkanForced460Core();
        ShaderDiskCache.configure(dir, "t");
        final SpirvCompiler.Result first = SpirvCompiler.compile(SRC, Shaderc.shaderc_vertex_shader, "disk-cache-test", opts);
        assertNotNull(first.spirv(), first.error());
        MemoryUtil.memFree(first.spirv());

        SpirvCompiler.clearCache();
        ShaderDiskCache.put(SpirvCompiler.diskKey(SRC, Shaderc.shaderc_vertex_shader, opts), new byte[] {1, 2, 3, 4});
        final SpirvCompiler.Result second = SpirvCompiler.compile(SRC, Shaderc.shaderc_vertex_shader, "disk-cache-test", opts);
        final ByteBuffer spirv = second.spirv();
        assertNotNull(spirv);
        try {
            assertArrayEquals(new byte[] {1, 2, 3, 4}, ShaderCacheIO.toHeap(spirv));
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void diskOptOutSkipsSpirvLayer() {
        ShaderDiskCache.configure(dir, "t");
        final SpirvCompiler.Result r = SpirvCompiler.compile(SRC, Shaderc.shaderc_vertex_shader, "disk-opt-out", SpirvCompiler.Options.vulkanForced460Core(), false);
        assertNotNull(r.spirv(), r.error());
        MemoryUtil.memFree(r.spirv());
        assertFalse(Files.exists(dir.resolve("spirv")));
    }

    @Test
    void redefinedBuiltinCompilesAndIsStoredUnderTheOriginalSource() {
        final SpirvCompiler.Options opts = SpirvCompiler.Options.vulkanForced460Core();
        ShaderDiskCache.configure(dir, "t");
        final SpirvCompiler.Result r = SpirvCompiler.compile(REDEFINES_CLAMP, Shaderc.shaderc_fragment_shader, "redefines-clamp", opts);
        assertNotNull(r.spirv(), r.error());
        MemoryUtil.memFree(r.spirv());
        assertNull(r.dumpPath());
        assertNotNull(ShaderDiskCache.get(SpirvCompiler.diskKey(REDEFINES_CLAMP, Shaderc.shaderc_fragment_shader, opts)));
    }

    @Test
    void brokenShaderWithHelperReportsTheOriginalError() throws IOException {
        final SpirvCompiler.Result r = SpirvCompiler.compile(BROKEN_WITH_HELPER, Shaderc.shaderc_fragment_shader, "broken-with-helper", SpirvCompiler.Options.vulkanForced460Core(), false);
        assertNull(r.spirv());
        assertTrue(r.error().contains("undeclared_var"), r.error());
        assertNotNull(r.dumpPath());
        assertFalse(Files.readString(r.dumpPath()).contains(GlslVulkanPreprocess.RESERVED_RENAMED_PREFIX));
    }
}
