package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SamplerBindGenerationTest {


    @Test
    void switchingActiveUnitDoesNotBumpTheGeneration() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();

        backend.activeTexture(GL13.GL_TEXTURE0);
        final int before = st.samplerBindGen;

        backend.activeTexture(GL13.GL_TEXTURE3);
        backend.activeTexture(GL13.GL_TEXTURE7);
        backend.activeTexture(GL13.GL_TEXTURE0);

        assertEquals(before, st.samplerBindGen, "a bare unit switch changes no binding");
        assertEquals(0, st.activeTextureUnit, "the active unit still tracks");
    }

    @Test
    void aRealBindChangeBumpsTheGeneration() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();

        backend.activeTexture(GL13.GL_TEXTURE0);
        backend.bindTexture(GL11.GL_TEXTURE_2D, 0);
        final int before = st.samplerBindGen;

        backend.bindTexture(GL11.GL_TEXTURE_2D, 42);
        assertNotEquals(before, st.samplerBindGen, "a changed binding must invalidate the sampler set");
    }

    @Test
    void rebindingTheSameTextureDoesNotBumpTheGeneration() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();

        backend.activeTexture(GL13.GL_TEXTURE0);
        backend.bindTexture(GL11.GL_TEXTURE_2D, 99);
        final int before = st.samplerBindGen;

        backend.bindTexture(GL11.GL_TEXTURE_2D, 99);
        assertEquals(before, st.samplerBindGen);
    }

    @Test
    void bindingsAreHeldPerUnitSoTheActiveUnitIsNotSamplerState() {
        final SDLGPURenderBackend backend = new SDLGPURenderBackend();
        final ContextState st = SdlTestRig.contextState();

        backend.activeTexture(GL13.GL_TEXTURE2);
        backend.bindTexture(GL11.GL_TEXTURE_2D, 55);
        backend.activeTexture(GL13.GL_TEXTURE6);

        assertEquals(55, st.boundTextures[2], "unit 2 keeps its binding after the active unit moves away");
    }
}
