package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.RED;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlDegenerateDrawTest {

    @ParameterizedTest(name = "count {0}")
    @ValueSource(ints = {0, -3})
    void degenerateCountDrawArraysLeavesTheFollowingDrawIntact(int count) {
        GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);
        GlsmSdlHeadlessRig.assertUniform(GlsmSdlHeadlessRig.readTarget(), RED, "after a drawArrays with count " + count);
    }
}
