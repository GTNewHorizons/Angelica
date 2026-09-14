package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlSamplerBindingTest {

    private static final int UNIT = 3;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void glsmDeleteClearsBothCaches() {
        final ContextState st = SdlTestRig.contextState();
        final int sampler = GLStateManager.glGenSamplers();
        GLStateManager.glBindSampler(UNIT, sampler);
        assertEquals(sampler, st.boundSamplerObjects[UNIT], "SDL unit binding");
        assertEquals(sampler, GLStateManager.getSamplerBinding(UNIT), "GLSM unit binding");

        GLStateManager.glDeleteSamplers(sampler);

        assertEquals(0, st.boundSamplerObjects[UNIT], "SDL must drop the deleted sampler");
        assertEquals(0, GLStateManager.getSamplerBinding(UNIT), "GLSM must drop the deleted sampler");
    }

    @Test
    void renderSystemRebindAfterGlsmUnbindReachesSdl() {
        assertTrue(RenderSystem.supportsSamplerObjects(), "RenderSystem sampler calls would no-op and prove nothing");
        final ContextState st = SdlTestRig.contextState();
        final int sampler = RenderSystem.genSampler();
        try {
            RenderSystem.bindSamplerToUnit(UNIT, sampler);
            GLStateManager.glBindSampler(UNIT, 0);
            assertEquals(0, st.boundSamplerObjects[UNIT], "setup: GLSM unbind must reach SDL");

            RenderSystem.bindSamplerToUnit(UNIT, sampler);

            assertEquals(sampler, st.boundSamplerObjects[UNIT], "RenderSystem skipped a bind SDL no longer holds");
        } finally {
            RenderSystem.destroySampler(sampler);
        }
    }

    @Test
    void unitCountMatchesTheSdlSamplerArray() {
        GLStateManager.glBindSampler(0, 0);
        assertEquals(SdlTestRig.contextState().boundSamplerObjects.length, GLStateManager.getSamplerUnitCount());
    }
}
