package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class GLSM_BlendFuncUnknownState_GLTest {

    private static final int VANILLA_SRC_RGB = GL11.GL_SRC_ALPHA;
    private static final int VANILLA_DST_RGB = GL11.GL_ONE_MINUS_SRC_ALPHA;
    private static final int VANILLA_SRC_ALPHA = GL11.GL_ONE;
    private static final int VANILLA_DST_ALPHA = GL11.GL_ZERO;

    @BeforeEach
    void requireBufferBlending() {
        assumeTrue(RenderSystem.supportsBufferBlending());
    }

    @AfterEach
    void restoreDefaultBlendFunc() {
        if (!RenderSystem.supportsBufferBlending()) return;
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ZERO);
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
    }

    private static void seedVanillaSeparate() {
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GLSMBlend.tryBlendFuncSeparate(VANILLA_SRC_RGB, VANILLA_DST_RGB, VANILLA_SRC_ALPHA, VANILLA_DST_ALPHA);
    }

    private static void overrideBufferZero() {
        RenderSystem.blendFuncSeparatei(0, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        assertBufferBlend(0, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
    }

    private static void assertBufferBlend(int buffer, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        assertEquals(srcRgb, GL30.glGetInteger(GL14.GL_BLEND_SRC_RGB, buffer), "buffer " + buffer + " src rgb");
        assertEquals(dstRgb, GL30.glGetInteger(GL14.GL_BLEND_DST_RGB, buffer), "buffer " + buffer + " dst rgb");
        assertEquals(srcAlpha, GL30.glGetInteger(GL14.GL_BLEND_SRC_ALPHA, buffer), "buffer " + buffer + " src alpha");
        assertEquals(dstAlpha, GL30.glGetInteger(GL14.GL_BLEND_DST_ALPHA, buffer), "buffer " + buffer + " dst alpha");
    }

    private static void assertVanillaOnBothBuffers() {
        for (int buffer = 0; buffer < 2; buffer++) {
            assertBufferBlend(buffer, VANILLA_SRC_RGB, VANILLA_DST_RGB, VANILLA_SRC_ALPHA, VANILLA_DST_ALPHA);
        }
        assertFalse(GLSMBlend.getBlendState().isFuncUnknown(), "forward must clear the unknown flag");
    }

    @Test
    void tryBlendFuncSeparateForwardsAfterIndexedOverride() {
        seedVanillaSeparate();
        overrideBufferZero();

        GLSMBlend.tryBlendFuncSeparate(VANILLA_SRC_RGB, VANILLA_DST_RGB, VANILLA_SRC_ALPHA, VANILLA_DST_ALPHA);

        assertVanillaOnBothBuffers();
    }

    @Test
    void glBlendFuncForwardsAfterIndexedOverride() {
        GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        overrideBufferZero();

        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int buffer = 0; buffer < 2; buffer++) {
            assertBufferBlend(buffer, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        assertFalse(GLSMBlend.getBlendState().isFuncUnknown(), "forward must clear the unknown flag");
    }

    @Test
    void popAttribForwardsAfterIndexedOverride() {
        seedVanillaSeparate();

        GLSMAttribStack.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
        try {
            overrideBufferZero();
        } finally {
            GLSMAttribStack.glPopAttrib();
        }

        assertVanillaOnBothBuffers();
    }

    @Test
    void displayListReplayForwardsAfterIndexedOverride() {
        final int list = GLSMDraws.glGenLists(1);
        try {
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMBlend.tryBlendFuncSeparate(VANILLA_SRC_RGB, VANILLA_DST_RGB, VANILLA_SRC_ALPHA, VANILLA_DST_ALPHA);
            GLSMDraws.glEndList();

            seedVanillaSeparate();
            overrideBufferZero();

            GLSMDraws.glCallList(list);

            assertVanillaOnBothBuffers();
        } finally {
            GLSMDraws.glDeleteLists(list, 1);
        }
    }
}
