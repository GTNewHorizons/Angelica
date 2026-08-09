package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.lwjgl.system.MemoryUtil.memFree;

class FragmentOutputReflectionTest {

    private static int maxOutputLocationOf(String source) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, Shaderc.shaderc_fragment_shader, "out.frag", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "compile failed: " + r.error());
        final ByteBuffer spirv = r.spirv();
        try {
            return ShaderManager.reflectStage(spirv, false).maxOutputLocation();
        } finally {
            memFree(spirv);
        }
    }

    @Test
    void irisStyleMultiTargetOutputs() {
        assertEquals(3, maxOutputLocationOf("""
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
            """));
    }

    @Test
    void twoTargetOutputs() {
        assertEquals(1, maxOutputLocationOf("""
            #version 460 core
            layout ( location = 1 ) out vec4 a ;
            layout ( location = 0 ) out vec4 b ;
            void main() { a = vec4(1.0); b = vec4(0.0); }
            """));
    }

    @Test
    void implicitLocationZeroIsFound() {
        assertEquals(0, maxOutputLocationOf("""
            #version 460 core
            out vec4 fragColor;
            void main() { fragColor = vec4(1.0); }
            """));
    }

    @Test
    void noOutputsYieldsMinusOne() {
        assertEquals(-1, maxOutputLocationOf("""
            #version 460 core
            void main() { }
            """));
    }

    @Test
    void vertexStageLeavesItUnset() {
        final SpirvCompiler.Result r = SpirvCompiler.compile("""
            #version 460 core
            layout ( location = 0 ) out vec4 vColor ;
            void main() { vColor = vec4(1.0); gl_Position = vec4(0.0); }
            """, Shaderc.shaderc_vertex_shader, "out.vert", SpirvCompiler.Options.vulkanForced460Core());
        assertNotNull(r.spirv(), () -> "compile failed: " + r.error());
        final ByteBuffer spirv = r.spirv();
        try {
            assertEquals(-1, ShaderManager.reflectStage(spirv, true).maxOutputLocation());
        } finally {
            memFree(spirv);
        }
    }
}
