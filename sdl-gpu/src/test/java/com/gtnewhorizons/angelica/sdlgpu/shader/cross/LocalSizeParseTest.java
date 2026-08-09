package com.gtnewhorizons.angelica.sdlgpu.shader.cross;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;

class LocalSizeParseTest {

    private static final String LS_8_8_1 = """
        #version 460 core
        layout(local_size_x = 8, local_size_y = 8) in;
        layout(std430, binding = 0) writeonly buffer B { uint b[]; } buf;
        void main() { buf.b[gl_GlobalInvocationID.x] = 1u; }
        """;

    private static final String LS_64 = """
        #version 460 core
        layout(local_size_x = 64) in;
        layout(std430, binding = 0) writeonly buffer B { uint b[]; } buf;
        void main() { buf.b[gl_GlobalInvocationID.x] = 1u; }
        """;

    private static final String LS_4_4_4 = """
        #version 460 core
        layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;
        layout(std430, binding = 0) writeonly buffer B { uint b[]; } buf;
        void main() { buf.b[gl_GlobalInvocationID.x] = 1u; }
        """;

    @Test
    void parsesLocalSize8x8x1() {
        final ByteBuffer spirv = compile(LS_8_8_1);
        try {
            assertArrayEquals(new int[]{8, 8, 1}, CrossCompileUtil.parseLocalSize(spirv));
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void parsesLocalSize64x1x1() {
        final ByteBuffer spirv = compile(LS_64);
        try {
            assertArrayEquals(new int[]{64, 1, 1}, CrossCompileUtil.parseLocalSize(spirv));
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void parsesLocalSize4x4x4() {
        final ByteBuffer spirv = compile(LS_4_4_4);
        try {
            assertArrayEquals(new int[]{4, 4, 4}, CrossCompileUtil.parseLocalSize(spirv));
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void noExecutionModeFallsBackTo111() {
        final ByteBuffer spirv = MemoryUtil.memAlloc(5 * 4).order(ByteOrder.nativeOrder());
        spirv.asIntBuffer().put(new int[]{0x07230203, 0x00010000, 0, 1, 0});
        try {
            assertArrayEquals(new int[]{1, 1, 1}, CrossCompileUtil.parseLocalSize(spirv));
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static ByteBuffer compile(String source) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_compute_shader, "ls_test",
            SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) {
            fail("Shader compilation failed: " + r.error());
        }
        return r.spirv();
    }
}
