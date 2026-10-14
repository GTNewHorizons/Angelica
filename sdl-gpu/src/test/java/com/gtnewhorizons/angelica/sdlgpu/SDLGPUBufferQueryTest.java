package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void boundTextureReadsTheUnitsSlot() {
        final ContextState st = new ContextState();
        st.boundTextures[0] = 11;
        st.boundTextures[3] = 44;
        assertEquals(11, st.boundTexture(0));
        assertEquals(44, st.boundTexture(3));
    }

    @Test
    void outOfRangeUnitReadsZero() {
        final ContextState st = new ContextState();
        st.boundTextures[0] = 11;
        assertEquals(0, st.boundTexture(-1));
        assertEquals(0, st.boundTexture(st.boundTextures.length));
        assertEquals(0, st.boundTexture(-33984));
    }

    @Test
    void deviceLimitsAreAnswered() {
        assertEquals(1024, SDLGPURenderBackend.deviceLimit(GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS));
        assertEquals(1024, SDLGPURenderBackend.deviceLimit(GL20.GL_MAX_FRAGMENT_UNIFORM_COMPONENTS));
        assertEquals(256, SDLGPURenderBackend.deviceLimit(GL30.GL_MAX_ARRAY_TEXTURE_LAYERS));
    }

    @Test
    void nonLimitPnamesAreNotClaimed() {
        assertEquals(-1, SDLGPURenderBackend.deviceLimit(GL11.GL_DRAW_BUFFER));
        assertEquals(-1, SDLGPURenderBackend.deviceLimit(0x1234));
    }
}
