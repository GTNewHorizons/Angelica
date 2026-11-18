package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.sdlgpu.shader.SpirvTestShaders.compileOrFail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    private static final String FRAGMENT_WITH_VEC3 = """
        #version 330 core
        uniform vec3 dir;
        out vec4 fragColor;
        void main() { fragColor = vec4(dir, 1.0); }
        """;

    private static final String FRAGMENT_WITH_MAT3 = """
        #version 330 core
        uniform mat3 normalMatrix;
        out vec4 fragColor;
        void main() { fragColor = vec4(normalMatrix * vec3(0,1,0), 1.0); }
        """;

    private static final int UNCHECKED = -1;

    private static Arguments member(String name, String source, String member, int vectorSize, int columns, int arrayLen, int arrayStride, int size) {
        return Arguments.of(name, source, member, vectorSize, columns, arrayLen, arrayStride, size);
    }

    static Stream<Arguments> members() {
        return Stream.of(
            member("int scalar", FRAGMENT_WITH_FLOAT_ARRAY, "idx", 1, 1, 1, 0, UNCHECKED),
            member("float[16]", FRAGMENT_WITH_FLOAT_ARRAY, "arr", 1, 1, 16, 16, 256),
            member("vec4[8]", FRAGMENT_WITH_VEC4_ARRAY, "colors", 4, 1, 8, 16, 128),
            member("vec3", FRAGMENT_WITH_VEC3, "dir", 3, 1, 1, 0, UNCHECKED),
            member("mat3", FRAGMENT_WITH_MAT3, "normalMatrix", 3, 3, 1, UNCHECKED, UNCHECKED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("members")
    void reflectsStd140MemberLayout(String name, String source, String member, int vectorSize, int columns, int arrayLen, int arrayStride, int size) {
        final ByteBuffer spirv = compileOrFail(source, Shaderc.shaderc_fragment_shader, SpirvCompiler.Options.vulkanRelaxed());
        try {
            final ShaderManager.StageReflection refl = ShaderManager.reflectStage(spirv, false);
            final ShaderManager.UboMember m = findMember(refl, member);
            assertNotNull(m, member + " missing from reflection: " + refl.uboMembers());
            assertEquals(vectorSize, m.vectorSize(), name + " vectorSize");
            assertEquals(columns, m.columns(), name + " columns");
            assertEquals(arrayLen, m.arrayLen(), name + " arrayLen (1 for non-arrays)");
            if (arrayStride != UNCHECKED) assertEquals(arrayStride, m.arrayStride(), name + " std140 arrayStride (0 for non-arrays)");
            if (size != UNCHECKED) assertEquals(size, m.size(), name + " std140 declared size");
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
}
