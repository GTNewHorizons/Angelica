package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlBlendConstantsTest {

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @AfterEach
    void restoreBlend() {
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glBlendColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    @Test
    void pendingConstantsSurviveADrawWithoutConstantBlending() {
        verifyConstants(false);
    }

    @Test
    void constantsChangedWhileBlendingIsDisabledReachTheNextBlendedDraw() {
        verifyConstants(true);
    }

    private static void verifyConstants(boolean previouslyEnabled) {
        GlsmSdlHeadlessRig.bindTarget();
        GLStateManager.glBlendFunc(GL14.GL_CONSTANT_COLOR, GL11.GL_ZERO);
        if (previouslyEnabled) {
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendColor(1.0f, 0.0f, 0.0f, 1.0f);
            GlsmSdlHeadlessRig.solidQuad(1.0f, 1.0f, 1.0f);
        }
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glBlendColor(0.0f, 1.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 1.0f, 1.0f);
        final FrameManager manager = Reflect.get(BackendManager.RENDER_BACKEND, "frameManager");
        final FrameManager.FrameState frame = manager.frame();
        final long pass = frame.renderPass;
        final long generation = frame.renderPassGeneration;
        assertNotEquals(0, pass);

        GLStateManager.glEnable(GL11.GL_BLEND);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 1.0f, 1.0f);
        assertEquals(pass, frame.renderPass);
        assertEquals(generation, frame.renderPassGeneration);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        final int size = GlsmSdlHeadlessRig.SIZE;
        assertEquals(0xFF00FF00, GlsmSdlHeadlessRig.pixelAt(pixels, size, size / 2, size / 2));
    }
}
