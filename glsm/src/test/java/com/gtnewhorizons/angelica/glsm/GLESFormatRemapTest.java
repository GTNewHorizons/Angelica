package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GLESFormatRemapTest {

    private static GLESFormatRemap.Result remap(int internal, int format, int type) {
        return GLESFormatRemap.apply(internal, format, type, /* isGLES = */ true);
    }

    private static GLESFormatRemap.Result remapDesktop(int internal, int format, int type) {
        return GLESFormatRemap.apply(internal, format, type, /* isGLES = */ false);
    }

    @Test
    void alphaSizedFormatsPromoteToRgba_onBothDesktopAndGles() {
        assertEquals(GL11.GL_RGBA4, remap(GL11.GL_ALPHA4, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).internalFormat());
        assertEquals(GL11.GL_RGBA8, remap(GL11.GL_ALPHA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).internalFormat());
        assertEquals(GL11.GL_RGBA4, remapDesktop(GL11.GL_ALPHA4, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).internalFormat());
    }

    static Stream<Arguments> remappedType() {
        return Stream.of(
            Arguments.of("R16F gets half float", true, GL30.GL_R16F, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, GL30.GL_HALF_FLOAT),
            Arguments.of("RG16F gets half float", true, GL30.GL_RG16F, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, GL30.GL_HALF_FLOAT),
            Arguments.of("RGB16F gets half float", true, GL30.GL_RGB16F, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, GL30.GL_HALF_FLOAT),
            Arguments.of("RGBA16F gets half float", true, GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL30.GL_HALF_FLOAT),
            Arguments.of("RGBA32F gets float", true, GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL11.GL_FLOAT),
            Arguments.of("RGB32F gets float", true, GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, GL11.GL_FLOAT),
            Arguments.of("RGBA8_SNORM gets byte", true, GL31.GL_RGBA8_SNORM, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL11.GL_BYTE),
            Arguments.of("RGB8_SNORM gets byte", true, GL31.GL_RGB8_SNORM, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, GL11.GL_BYTE),
            Arguments.of("RGB10_A2 gets packed type", true, GL11.GL_RGB10_A2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL12.GL_UNSIGNED_INT_2_10_10_10_REV),
            Arguments.of("DEPTH_COMPONENT24 gets unsigned int", true, GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_BYTE, GL11.GL_UNSIGNED_INT),
            Arguments.of("DEPTH_COMPONENT32F gets float", true, GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_BYTE, GL11.GL_FLOAT),
            Arguments.of("DEPTH24_STENCIL8 gets 24_8", true, GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL11.GL_UNSIGNED_BYTE, GL30.GL_UNSIGNED_INT_24_8),
            Arguments.of("already valid type is left alone", true, GL30.GL_RGBA16F, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, GL30.GL_HALF_FLOAT),
            Arguments.of("packed ARGB RGBA remaps to unsigned byte on GLES", true, GL11.GL_RGBA8, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, GL11.GL_UNSIGNED_BYTE),
            Arguments.of("packed ARGB BGRA remaps to unsigned byte on GLES", true, GL11.GL_RGBA8, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, GL11.GL_UNSIGNED_BYTE),
            Arguments.of("packed ARGB untouched on desktop", false, GL11.GL_RGBA8, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, GL12.GL_UNSIGNED_INT_8_8_8_8_REV));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void remappedType(String label, boolean gles, int internal, int format, int type, int expectedType) {
        assertEquals(expectedType, GLESFormatRemap.apply(internal, format, type, gles).type());
    }

    static Stream<Arguments> remappedFormatAndType() {
        return Stream.of(
            Arguments.of("RGB16 remaps to half float on GLES", true, GL11.GL_RGB16, GL11.GL_RGB, GL30.GL_RGB16F, GL30.GL_HALF_FLOAT),
            Arguments.of("RGBA16 remaps to half float on GLES", true, GL11.GL_RGBA16, GL11.GL_RGBA, GL30.GL_RGBA16F, GL30.GL_HALF_FLOAT),
            Arguments.of("RGB16 passes through untouched on desktop", false, GL11.GL_RGB16, GL11.GL_RGB, GL11.GL_RGB16, GL11.GL_UNSIGNED_BYTE),
            Arguments.of("R11F_G11F_B10F keeps its format and gets half float", true, GL30.GL_R11F_G11F_B10F, GL11.GL_RGB, GL30.GL_R11F_G11F_B10F, GL30.GL_HALF_FLOAT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void remappedFormatAndType(String label, boolean gles, int internal, int format, int expectedInternal, int expectedType) {
        final GLESFormatRemap.Result r = GLESFormatRemap.apply(internal, format, GL11.GL_UNSIGNED_BYTE, gles);
        assertEquals(expectedInternal, r.internalFormat());
        assertEquals(expectedType, r.type());
    }
}
