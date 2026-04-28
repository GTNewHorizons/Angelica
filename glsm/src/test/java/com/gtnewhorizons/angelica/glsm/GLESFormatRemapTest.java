package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

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
        // Alpha promotion runs regardless of isGLES; the promoted form may then be further
        // remapped under GLES (e.g. RGBA16 -> RGBA16F).
        assertEquals(GL11.GL_RGBA4, remap(GL11.GL_ALPHA4, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).internalFormat());
        assertEquals(GL11.GL_RGBA8, remap(GL11.GL_ALPHA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).internalFormat());
        assertEquals(GL11.GL_RGBA4, remapDesktop(GL11.GL_ALPHA4, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).internalFormat());
    }

    @Test
    void rgb16AndRgba16_remapToHalfFloat_onGles() {
        final var r1 = remap(GL11.GL_RGB16, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE);
        assertEquals(GL30.GL_RGB16F, r1.internalFormat());
        assertEquals(GL30.GL_HALF_FLOAT, r1.type());

        final var r2 = remap(GL11.GL_RGBA16, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        assertEquals(GL30.GL_RGBA16F, r2.internalFormat());
        assertEquals(GL30.GL_HALF_FLOAT, r2.type());
    }

    @Test
    void rgb16AndRgba16_passThroughUntouched_onDesktop() {
        final var r = remapDesktop(GL11.GL_RGB16, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE);
        assertEquals(GL11.GL_RGB16, r.internalFormat());
        assertEquals(GL11.GL_UNSIGNED_BYTE, r.type());
    }

    @Test
    void halfFloatInternalFormats_getHalfFloatType() {
        assertEquals(GL30.GL_HALF_FLOAT, remap(GL30.GL_R16F, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE).type());
        assertEquals(GL30.GL_HALF_FLOAT, remap(GL30.GL_RG16F, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE).type());
        assertEquals(GL30.GL_HALF_FLOAT, remap(GL30.GL_RGB16F, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE).type());
        assertEquals(GL30.GL_HALF_FLOAT, remap(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).type());
    }

    @Test
    void floatInternalFormats_getFloatType() {
        assertEquals(GL11.GL_FLOAT, remap(GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).type());
        assertEquals(GL11.GL_FLOAT, remap(GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE).type());
    }

    @Test
    void snormInternalFormats_getByteType() {
        assertEquals(GL11.GL_BYTE, remap(GL31.GL_RGBA8_SNORM, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE).type());
        assertEquals(GL11.GL_BYTE, remap(GL31.GL_RGB8_SNORM, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE).type());
    }

    @Test
    void r11fG11fB10f_getsHalfFloatType() {
        final var r = remap(GL30.GL_R11F_G11F_B10F, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE);
        assertEquals(GL30.GL_R11F_G11F_B10F, r.internalFormat()); // not remapped
        assertEquals(GL30.GL_HALF_FLOAT, r.type());
    }

    @Test
    void rgb10A2_getsPackedType() {
        final var r = remap(GL11.GL_RGB10_A2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        assertEquals(GL12.GL_UNSIGNED_INT_2_10_10_10_REV, r.type());
    }

    @Test
    void depthFormats_getMatchingType() {
        assertEquals(GL11.GL_UNSIGNED_INT, remap(GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_BYTE).type());
        assertEquals(GL11.GL_FLOAT, remap(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_BYTE).type());
        assertEquals(GL30.GL_UNSIGNED_INT_24_8, remap(GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL11.GL_UNSIGNED_BYTE).type());
    }

    @Test
    void alreadyValidType_isLeftAlone() {
        // Caller passed HALF_FLOAT; must be preserved even though format is RGBA16F.
        final var r = remap(GL30.GL_RGBA16F, GL11.GL_RGBA, GL30.GL_HALF_FLOAT);
        assertEquals(GL30.GL_HALF_FLOAT, r.type());
    }

    @Test
    void packedArgb_remapsToUnsignedByte_onGles() {
        // GL_RGBA + GL_UNSIGNED_INT_8_8_8_8_REV -> GL_RGBA + GL_UNSIGNED_BYTE (little-endian equivalence).
        final var r = remap(GL11.GL_RGBA8, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV);
        assertEquals(GL11.GL_UNSIGNED_BYTE, r.type());

        final var r2 = remap(GL11.GL_RGBA8, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV);
        assertEquals(GL11.GL_UNSIGNED_BYTE, r2.type());
    }

    @Test
    void packedArgb_untouched_onDesktop() {
        final var r = remapDesktop(GL11.GL_RGBA8, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV);
        assertEquals(GL12.GL_UNSIGNED_INT_8_8_8_8_REV, r.type());
    }
}
