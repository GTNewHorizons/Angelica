package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlDegenerateDrawTest {

    private static final int RED = 0xFFFF0000;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void zeroCountDrawArraysLeavesTheFollowingDrawIntact() {
        GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 0);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);
        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(), RED, "after a zero-count drawArrays");
    }

    @Test
    void negativeCountDrawArraysLeavesTheFollowingDrawIntact() {
        GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, -3);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);
        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(), RED, "after a negative-count drawArrays");
    }
}
