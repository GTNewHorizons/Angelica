package com.gtnewhorizons.angelica.sdlgpu;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SDLGPUBufferQueryTest {

    @Test
    void fbo0IsTheSwapchainAndReadsAsBack() {
        assertEquals(GL11.GL_BACK, SDLGPURenderBackend.bufferEnum(0, 0));
        assertEquals(GL11.GL_BACK, SDLGPURenderBackend.bufferEnum(0, 3), "FBO 0 has no configurable draw buffer");
        assertEquals(GL11.GL_BACK, SDLGPURenderBackend.bufferEnum(0, -1));
    }

    @Test
    void boundFboReadsAsItsColourAttachment() {
        assertEquals(GL30.GL_COLOR_ATTACHMENT0, SDLGPURenderBackend.bufferEnum(7, 0));
        assertEquals(GL30.GL_COLOR_ATTACHMENT0 + 3, SDLGPURenderBackend.bufferEnum(7, 3));
    }

    @Test
    void emptyDrawBufferListReadsAsNone() {
        assertEquals(GL11.GL_NONE, SDLGPURenderBackend.bufferEnum(7, -1));
    }

    @Test
    void defaultMatchesGLStateManagerDeclaredDefault() {
        assertEquals(0x405, SDLGPURenderBackend.bufferEnum(0, 0));
    }

    @Test
    void boundTextureReadsTheActiveUnitsSlot() {
        final int[] bound = {11, 22, 33, 44};
        assertEquals(11, SDLGPURenderBackend.boundTextureOf(0, bound));
        assertEquals(44, SDLGPURenderBackend.boundTextureOf(3, bound));
    }

    @Test
    void outOfRangeUnitReadsZero() {
        final int[] bound = {11, 22};
        assertEquals(0, SDLGPURenderBackend.boundTextureOf(-1, bound));
        assertEquals(0, SDLGPURenderBackend.boundTextureOf(2, bound));
        assertEquals(0, SDLGPURenderBackend.boundTextureOf(-33984, bound));
        assertEquals(0, SDLGPURenderBackend.boundTextureOf(0, new int[0]));
    }

    @Test
    void deviceLimitsAreAnswered() {
        assertEquals(1024, SDLGPURenderBackend.deviceLimit(GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS));
        assertEquals(1024, SDLGPURenderBackend.deviceLimit(GL20.GL_MAX_FRAGMENT_UNIFORM_COMPONENTS));
        assertEquals(256, SDLGPURenderBackend.deviceLimit(GL30.GL_MAX_ARRAY_TEXTURE_LAYERS));
    }

    @Test
    void uniformLimitFitsSdlsUboSection() {
        assertTrue(SDLGPURenderBackend.deviceLimit(GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS) * 4 <= 4096);
        assertTrue(SDLGPURenderBackend.deviceLimit(GL30.GL_MAX_ARRAY_TEXTURE_LAYERS) <= 256);
    }

    @Test
    void nonLimitPnamesAreNotClaimed() {
        assertEquals(-1, SDLGPURenderBackend.deviceLimit(GL11.GL_DRAW_BUFFER));
        assertEquals(-1, SDLGPURenderBackend.deviceLimit(0x1234));
    }
}
