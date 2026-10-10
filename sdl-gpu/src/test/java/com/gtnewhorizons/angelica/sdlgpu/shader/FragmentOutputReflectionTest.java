package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders.compileOrFail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryUtil.memFree;

class FragmentOutputReflectionTest {

    private static int maxOutputLocationOf(String source) {
        final ByteBuffer spirv = compileOrFail(source, Shaderc.shaderc_fragment_shader);
        try {
            return ShaderManager.reflectStage(spirv, false).maxOutputLocation();
        } finally {
            memFree(spirv);
        }
    }

    static Stream<Arguments> fragments() {
        return Stream.of(
            Arguments.of("irisStyleMultiTargetOutputs", 3, """
                #version 460 core
                layout ( location = 3 ) out vec4 iris_FragData3 ;
                layout ( location = 2 ) out vec4 iris_FragData2 ;
                layout ( location = 1 ) out vec4 iris_FragData1 ;
                layout ( location = 0 ) out vec4 iris_FragData0 ;
                void main() {
                    iris_FragData0 = vec4(1.0);
                    iris_FragData1 = vec4(2.0);
                    iris_FragData2 = vec4(3.0);
                    iris_FragData3 = vec4(4.0);
                }
                """),
            Arguments.of("twoTargetOutputs", 1, """
                #version 460 core
                layout ( location = 1 ) out vec4 a ;
                layout ( location = 0 ) out vec4 b ;
                void main() { a = vec4(1.0); b = vec4(0.0); }
                """),
            Arguments.of("implicitLocationZeroIsFound", 0, """
                #version 460 core
                out vec4 fragColor;
                void main() { fragColor = vec4(1.0); }
                """),
            Arguments.of("noOutputsYieldsMinusOne", -1, """
                #version 460 core
                void main() { }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fragments")
    void maxOutputLocation(String name, int expected, String source) {
        assertEquals(expected, maxOutputLocationOf(source));
    }

    @Test
    void vertexStageLeavesItUnset() {
        final ByteBuffer spirv = compileOrFail("""
            #version 460 core
            layout ( location = 0 ) out vec4 vColor ;
            void main() { vColor = vec4(1.0); gl_Position = vec4(0.0); }
            """, Shaderc.shaderc_vertex_shader);
        try {
            assertEquals(-1, ShaderManager.reflectStage(spirv, true).maxOutputLocation());
        } finally {
            memFree(spirv);
        }
    }
}
