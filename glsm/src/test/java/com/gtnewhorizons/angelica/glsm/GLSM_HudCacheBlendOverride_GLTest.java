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
        assertEquals(srcRgb, GLStateManager.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(dstRgb, GLStateManager.glGetInteger(GL14.GL_BLEND_DST_RGB));
        assertEquals(srcAlpha, GLStateManager.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
        assertEquals(dstAlpha, GLStateManager.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
    }

    @Test
    void overrideChangesTheDriverFuncButNotTheCache() {
        try {
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            GLStateManager.setHudCacheOverride(true);
            assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            assertCachedFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            assertDriverFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GLStateManager.setHudCacheOverride(false);
            assertCachedFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            assertDriverFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
        } finally {
            GLStateManager.setHudCacheOverride(false);
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void popUnderOverrideKeepsTheOverriddenAlphaOnTheDriver() {
        try {
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            final int d = GLStateManager.pushState(StateSet.BLEND);
            GLStateManager.setHudCacheOverride(true);
            GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            GLStateManager.popStateTo(d);
            assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.setHudCacheOverride(false);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        } finally {
            GLStateManager.setHudCacheOverride(false);
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    @Test
    void separateAlphaIsForcedEvenWhenDstAlphaAlreadyMatches() {
        try {
            GLStateManager.setHudCacheOverride(true);
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
            assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } finally {
            GLStateManager.setHudCacheOverride(false);
            GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }
}
