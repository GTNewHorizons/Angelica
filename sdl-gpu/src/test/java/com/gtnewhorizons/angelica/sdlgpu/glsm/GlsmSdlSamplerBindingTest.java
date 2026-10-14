package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlSamplerBindingTest {

    private static final int UNIT = 3;

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
    void pointingASamplerAtAnotherUnitRebindsItBeforeTheNextDraw() {
        final int program = GlTestSupport.program(GlsmSdlHeadlessRig.FULLSCREEN_VERTEX, """
            #version 330 core
            uniform sampler2D s;
            out vec4 fragColor;
            void main() { fragColor = texture(s, vec2(0.5)); }
            """);
        final int red = GlsmSdlHeadlessRig.createSolidTexture(0xFFFF0000);
        final int green = GlsmSdlHeadlessRig.createSolidTexture(0xFF00FF00);
        final int vao = GLStateManager.glGenVertexArrays();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, red);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, green);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glUseProgram(program);
            final int location = GLStateManager.glGetUniformLocation(program, "s");
            GLStateManager.glUniform1i(location, 0);
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            GLStateManager.glUniform1i(location, 1);
            GLStateManager.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            GLStateManager.glUseProgram(0);

            final int center = GlsmSdlHeadlessRig.readTarget(GlsmSdlHeadlessRig.SIZE / 2, GlsmSdlHeadlessRig.SIZE / 2, 1, 1)[0];
            assertEquals(Integer.toHexString(GlsmSdlHeadlessRig.GREEN), Integer.toHexString(center), "the second draw must sample unit 1");
        } finally {
            GLStateManager.glBindVertexArray(0);
            GLStateManager.glDeleteVertexArrays(vao);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(red);
            GLStateManager.glDeleteTextures(green);
            GLStateManager.glDeleteProgram(program);
        }
    }

    @Test
    void unitCountMatchesTheSdlSamplerArray() {
        GLStateManager.glBindSampler(0, 0);
        assertEquals(SdlTestRig.contextState().boundSamplerObjects.length, GLStateManager.getSamplerUnitCount());
    }
}
