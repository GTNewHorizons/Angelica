package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class ReadbackGLTest {

    @AfterEach
    void noErrors() {
        ReadbackFixture.resetPack();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void rgba8() {
        ReadbackFixture.rgba8();
    }

    @Test
    void packState() {
        ReadbackFixture.packState();
    }

    @Test
    void pixelPackBuffer() {
        ReadbackFixture.pixelPackBuffer();
    }

    @Test
    void rgb8() {
        ReadbackFixture.rgb8();
    }

    @Test
    void rgba16f() {
        ReadbackFixture.rgba16f();
    }

    @Test
    void rgba32f() {
        ReadbackFixture.rgba32f();
    }

    @Test
    void r8() {
        ReadbackFixture.r8();
    }

    @Test
    void rg16f() {
        ReadbackFixture.rg16f();
    }

    @Test
    void rgb10a2() {
        ReadbackFixture.rgb10a2();
    }

    @Test
    void r11g11b10f() {
        ReadbackFixture.r11g11b10f();
    }

    @Test
    void depthStencil() {
        ReadbackFixture.depthStencil();
    }
}
