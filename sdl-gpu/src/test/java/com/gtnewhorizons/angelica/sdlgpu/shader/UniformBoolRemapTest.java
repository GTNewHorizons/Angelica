package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.sdlgpu.resource.FormatMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniformBoolRemapTest {

    static Stream<Arguments> unsignedTypes() {
        return Stream.of(
            Arguments.of("uint -> bool", GL11.GL_UNSIGNED_INT, GL20.GL_BOOL),
            Arguments.of("uvec2 -> bvec2", GL30.GL_UNSIGNED_INT_VEC2, GL20.GL_BOOL_VEC2),
            Arguments.of("uvec3 -> bvec3", GL30.GL_UNSIGNED_INT_VEC3, GL20.GL_BOOL_VEC3),
            Arguments.of("uvec4 -> bvec4", GL30.GL_UNSIGNED_INT_VEC4, GL20.GL_BOOL_VEC4));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsignedTypes")
    void unsignedIntTypeRemapsToBool(String name, int unsignedType, int boolType) {
        assertEquals(boolType, FormatMap.remapUnsignedIntToBool(unsignedType));
    }

    @Test
    void nonUintPassesThrough() {
        assertEquals(GL20.GL_FLOAT_VEC4, FormatMap.remapUnsignedIntToBool(GL20.GL_FLOAT_VEC4));
        assertEquals(GL11.GL_INT, FormatMap.remapUnsignedIntToBool(GL11.GL_INT));
        assertEquals(GL20.GL_BOOL, FormatMap.remapUnsignedIntToBool(GL20.GL_BOOL));
        assertEquals(GL20.GL_FLOAT_MAT4, FormatMap.remapUnsignedIntToBool(GL20.GL_FLOAT_MAT4));
    }
}
