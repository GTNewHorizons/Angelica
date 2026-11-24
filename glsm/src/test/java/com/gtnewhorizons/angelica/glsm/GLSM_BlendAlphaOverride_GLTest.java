package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class GLSM_BlendAlphaOverride_GLTest {

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

    @AfterEach
    void resetOverrides() {
        GLSMHooks.blendHandler = null;
        GLSMBlend.setHudCacheOverride(false);
        GLSMBlend.setLayerBlending(false);
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    @Test
    void overrideChangesTheDriverFuncButNotTheCache() {
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
    }

    @Test
    void popUnderOverrideKeepsTheOverriddenAlphaOnTheDriver() {
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        final int d = GLSMAttribStack.pushState(StateSet.BLEND);
        GLSMBlend.setHudCacheOverride(true);
        GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GLSMAttribStack.popStateTo(d);
        assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLSMBlend.setHudCacheOverride(false);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    @Test
    void separateAlphaIsForcedEvenWhenDstAlphaAlreadyMatches() {
        GLSMBlend.setHudCacheOverride(true);
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
        assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    static Stream<Arguments> layerBlendRows() {
        return Stream.of(
            Arguments.of("translucent over", GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA),
            Arguments.of("alpha additive", GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layerBlendRows")
    void layerBlendingAccumulatesCoverageAndKeepsAdditiveCoverageFree(String name, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        GLSMBlend.setLayerBlending(true);
        GLSMBlend.glBlendFunc(srcRgb, dstRgb);
        assertCachedFunc(srcRgb, dstRgb, srcRgb, dstRgb);
        assertDriverFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GLSMBlend.setLayerBlending(false);
        assertDriverFunc(srcRgb, dstRgb, srcRgb, dstRgb);
    }

    @Test
    void togglingWhileABlendOverrideIsHeldReemitsOnTheNextUnchangedFunc() {
        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLSMHooks.blendHandler = new HeldOverride();
        GLSMBlend.setLayerBlending(true);
        GLSMHooks.blendHandler = null;
        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static final class HeldOverride implements DeferredBlendHandler {
        @Override public boolean isBlendLocked() { return false; }
        @Override public boolean isOverrideHeld() { return true; }
        @Override public void deferBlendModeToggle(boolean enabled) {}
        @Override public void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {}
        @Override public void flushDeferredBlend() {}
    }
}
