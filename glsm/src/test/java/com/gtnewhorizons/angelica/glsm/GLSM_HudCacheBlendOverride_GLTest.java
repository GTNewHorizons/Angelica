package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class GLSM_HudCacheBlendOverride_GLTest {

    private static void assertDriverFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        assertEquals(srcRgb, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(dstRgb, GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));
        assertEquals(srcAlpha, GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
        assertEquals(dstAlpha, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
    }

    private static void assertCachedFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        assertEquals(srcRgb, GLSMQueries.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(dstRgb, GLSMQueries.glGetInteger(GL14.GL_BLEND_DST_RGB));
        assertEquals(srcAlpha, GLSMQueries.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
        assertEquals(dstAlpha, GLSMQueries.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
    }

    @Test
    void overrideChangesTheDriverFuncButNotTheCache() {
        try {
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            GLSMBlend.setHudCacheOverride(true);
            assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            assertCachedFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            assertDriverFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GLSMBlend.setHudCacheOverride(false);
            assertCachedFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            assertDriverFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
        } finally {
            GLSMBlend.setHudCacheOverride(false);
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void popUnderOverrideKeepsTheOverriddenAlphaOnTheDriver() {
        try {
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            final int d = GLSMAttribStack.pushState(StateSet.BLEND);
            GLSMBlend.setHudCacheOverride(true);
            GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            GLSMAttribStack.popStateTo(d);
            assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLSMBlend.setHudCacheOverride(false);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        } finally {
            GLSMBlend.setHudCacheOverride(false);
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void separateAlphaIsForcedEvenWhenDstAlphaAlreadyMatches() {
        try {
            GLSMBlend.setHudCacheOverride(true);
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
            assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } finally {
            GLSMBlend.setHudCacheOverride(false);
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }
}
