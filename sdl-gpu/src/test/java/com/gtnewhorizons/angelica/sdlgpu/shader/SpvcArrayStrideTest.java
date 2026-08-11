package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class SpvcArrayStrideTest {

    private static final String FRAGMENT_WITH_FLOAT_ARRAY = """
        #version 330 core
        uniform float arr[16];
        uniform int idx;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(arr[idx], arr[(idx + 1) % 16], 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT_WITH_VEC4_ARRAY = """
        #version 330 core
        uniform vec4 colors[8];
        uniform int idx;
        out vec4 fragColor;
        void main() {
            fragColor = colors[idx];
        }
        """;

    @Test
    void floatArrayHasStride16() {
        final ByteBuffer spirv = compile(FRAGMENT_WITH_FLOAT_ARRAY, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            final ShaderManager.UboMember arr = findMember(refl, "arr");
            assertNotNull(arr, "arr member missing from reflection: " + refl.uboMembers());
            assertEquals(16, arr.arrayStride(), "float[N] array stride should be 16 bytes under std140");
            assertEquals(256, arr.size(), "float[16] declared size should be 256 bytes under std140");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void vec4ArrayHasStride16() {
        final ByteBuffer spirv = compile(FRAGMENT_WITH_VEC4_ARRAY, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            final ShaderManager.UboMember arr = findMember(refl, "colors");
            assertNotNull(arr, "colors member missing from reflection: " + refl.uboMembers());
            assertEquals(16, arr.arrayStride(), "vec4[N] array stride should be 16 bytes under std140");
            assertEquals(128, arr.size(), "vec4[8] declared size should be 128 bytes under std140");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void scalarMemberHasZeroStride() {
        final ByteBuffer spirv = compile(FRAGMENT_WITH_FLOAT_ARRAY, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            final ShaderManager.UboMember idx = findMember(refl, "idx");
            assertNotNull(idx, "idx member missing from reflection: " + refl.uboMembers());
            assertEquals(0, idx.arrayStride(), "non-array members must have arrayStride=0");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void typeDescriptor_floatScalar() {
        final ByteBuffer spirv = compile(FRAGMENT_WITH_FLOAT_ARRAY, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.UboMember idx = findMember(ShaderManager.reflectStage(spirv, false), "idx");
            assertNotNull(idx);
            assertEquals(1, idx.vectorSize(), "scalar vectorSize");
            assertEquals(1, idx.columns(), "scalar columns");
            assertEquals(1, idx.arrayLen(), "non-array arrayLen=1");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void typeDescriptor_floatArray() {
        final ByteBuffer spirv = compile(FRAGMENT_WITH_FLOAT_ARRAY, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.UboMember arr = findMember(ShaderManager.reflectStage(spirv, false), "arr");
            assertNotNull(arr);
            assertEquals(1, arr.vectorSize(), "scalar array vectorSize");
            assertEquals(1, arr.columns(), "scalar array columns");
            assertEquals(16, arr.arrayLen(), "float[16] arrayLen");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    @Test
    void typeDescriptor_vec4Array() {
        final ByteBuffer spirv = compile(FRAGMENT_WITH_VEC4_ARRAY, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.UboMember colors = findMember(ShaderManager.reflectStage(spirv, false), "colors");
            assertNotNull(colors);
            assertEquals(4, colors.vectorSize(), "vec4 vectorSize");
            assertEquals(1, colors.columns(), "vec4 columns");
            assertEquals(8, colors.arrayLen(), "vec4[8] arrayLen");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static final String FRAGMENT_WITH_VEC3 = """
        #version 330 core
        uniform vec3 dir;
        out vec4 fragColor;
        void main() { fragColor = vec4(dir, 1.0); }
        """;

    @Test
    void typeDescriptor_vec3() {
        final ByteBuffer spirv = compile(FRAGMENT_WITH_VEC3, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.UboMember dir = findMember(ShaderManager.reflectStage(spirv, false), "dir");
            assertNotNull(dir);
            assertEquals(3, dir.vectorSize(), "vec3 vectorSize");
            assertEquals(1, dir.columns(), "vec3 columns");
            assertEquals(1, dir.arrayLen(), "non-array");
            assertEquals(0, dir.arrayStride(), "non-array stride=0");
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static final String FRAGMENT_WITH_MAT3 = """
        #version 330 core
        uniform mat3 normalMatrix;
        out vec4 fragColor;
        void main() { fragColor = vec4(normalMatrix * vec3(0,1,0), 1.0); }
        """;

    @Test
    void typeDescriptor_mat3() {
        final ByteBuffer spirv = compile(FRAGMENT_WITH_MAT3, Shaderc.shaderc_fragment_shader);
        try {
            final ShaderManager.UboMember m = findMember(ShaderManager.reflectStage(spirv, false), "normalMatrix");
            assertNotNull(m);
            assertEquals(3, m.vectorSize(), "mat3 rows");
            assertEquals(3, m.columns(), "mat3 columns");
            assertEquals(1, m.arrayLen(), "non-array");

        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    private static ShaderManager.UboMember findMember(ShaderManager.StageReflection refl, String name) {
        for (ShaderManager.UboMember m : refl.uboMembers()) {
            if (name.equals(m.name())) return m;
        }
        return null;
    }

    private static ByteBuffer compile(String source, int shaderKind) {
        final SpirvCompiler.Result r = SpirvCompiler.compile(source, shaderKind, "test", SpirvCompiler.Options.vulkanRelaxed());
        if (r.spirv() == null) {
            fail("Compilation failed: " + r.error());
        }
        return r.spirv();
    }
}
