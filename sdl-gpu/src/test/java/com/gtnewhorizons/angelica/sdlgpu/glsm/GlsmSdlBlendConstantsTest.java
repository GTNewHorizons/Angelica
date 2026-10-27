package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlBlendConstantsTest {

    @AfterEach
    void restoreBlend() {
        GLSMEnable.glDisable(GL11.GL_BLEND);
        GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GLSMBlend.glBlendColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    /** With blending never enabled before, the pending constants must survive a draw that does not use them. */
    @ParameterizedTest(name = "blending enabled earlier in the frame: {0}")
    @ValueSource(booleans = {false, true})
    void constantsSetWhileBlendingIsDisabledReachTheNextBlendedDraw(boolean previouslyEnabled) {
        GlsmSdlHeadlessRig.bindTarget();
        GLSMBlend.glBlendFunc(GL14.GL_CONSTANT_COLOR, GL11.GL_ZERO);
        if (previouslyEnabled) {
            GLSMEnable.glEnable(GL11.GL_BLEND);
            GLSMBlend.glBlendColor(1.0f, 0.0f, 0.0f, 1.0f);
            GlsmSdlHeadlessRig.solidQuad(1.0f, 1.0f, 1.0f);
        }
        GLSMEnable.glDisable(GL11.GL_BLEND);
        GLSMBlend.glBlendColor(0.0f, 1.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 1.0f, 1.0f);
        final FrameManager.FrameState frame = GlsmSdlHeadlessRig.frameManager().frame();
        final long pass = frame.renderPass;
        final long generation = frame.renderPassGeneration;
        assertNotEquals(0, pass);

        GLSMEnable.glEnable(GL11.GL_BLEND);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 1.0f, 1.0f);
        assertEquals(pass, frame.renderPass);
        assertEquals(generation, frame.renderPassGeneration);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        final int size = GlsmSdlHeadlessRig.SIZE;
        assertEquals(GlsmSdlHeadlessRig.GREEN, GlsmSdlHeadlessRig.pixelAt(pixels, size, size / 2, size / 2));
    }
}
