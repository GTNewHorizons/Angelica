package com.gtnewhorizons.angelica.sdlgpu.shader.cross;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders.compileOrFail;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

    static Stream<Arguments> localSizes() {
        return Stream.of(
            Arguments.of("8x8x1", LS_8_8_1, new int[]{8, 8, 1}),
            Arguments.of("64x1x1", LS_64, new int[]{64, 1, 1}),
            Arguments.of("4x4x4", LS_4_4_4, new int[]{4, 4, 4}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("localSizes")
    void parsesLocalSize(String name, String source, int[] expected) {
        final ByteBuffer spirv = compileOrFail(source, Shaderc.shaderc_compute_shader);
        try {
            assertArrayEquals(expected, CrossCompileUtil.parseLocalSize(spirv));
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
}
