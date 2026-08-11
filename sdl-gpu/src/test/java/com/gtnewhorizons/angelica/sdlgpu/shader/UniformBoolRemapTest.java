package com.gtnewhorizons.angelica.sdlgpu.shader;


import com.gtnewhorizons.angelica.sdlgpu.resource.FormatMap;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniformBoolRemapTest {

    @Test
    void scalarUintToBool() {
        assertEquals(GL20.GL_BOOL, FormatMap.remapUnsignedIntToBool(GL11.GL_UNSIGNED_INT));
    }

    @Test
    void uvec2ToBvec2() {
        assertEquals(GL20.GL_BOOL_VEC2, FormatMap.remapUnsignedIntToBool(GL30.GL_UNSIGNED_INT_VEC2));
    }

    @Test
    void uvec3ToBvec3() {
        assertEquals(GL20.GL_BOOL_VEC3, FormatMap.remapUnsignedIntToBool(GL30.GL_UNSIGNED_INT_VEC3));
    }

    @Test
    void uvec4ToBvec4() {
        assertEquals(GL20.GL_BOOL_VEC4, FormatMap.remapUnsignedIntToBool(GL30.GL_UNSIGNED_INT_VEC4));
    }

    @Test
    void nonUintPassesThrough() {
        assertEquals(GL20.GL_FLOAT_VEC4, FormatMap.remapUnsignedIntToBool(GL20.GL_FLOAT_VEC4));
        assertEquals(GL11.GL_INT, FormatMap.remapUnsignedIntToBool(GL11.GL_INT));
        assertEquals(GL20.GL_BOOL, FormatMap.remapUnsignedIntToBool(GL20.GL_BOOL));
        assertEquals(GL20.GL_FLOAT_MAT4, FormatMap.remapUnsignedIntToBool(GL20.GL_FLOAT_MAT4));
    }
}
