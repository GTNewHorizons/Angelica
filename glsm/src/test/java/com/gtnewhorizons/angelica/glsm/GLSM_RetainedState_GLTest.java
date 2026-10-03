package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.stacks.RetainedState;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_RetainedState_GLTest {

    private static final StateSet ALL = StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS);

    private static int driverTextureBinding(int unit) {
        final int saved = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        final int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(saved);
        return binding;
    }

    private static void baseline() {
        GLStateManager.enableCull();
        GLStateManager.disableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glDepthMask(true);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private static void mutate(int texture) {
        GLStateManager.disableCull();
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE);
        GLStateManager.glDepthMask(false);
        GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
        GLStateManager.glAlphaFunc(GL11.GL_GEQUAL, 0.5F);
        GLStateManager.glColor4f(0.25F, 0.5F, 0.75F, 0.5F);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static void assertBaseline() {
        assertTrue(GLStateManager.glIsEnabled(GL11.GL_CULL_FACE));
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        assertFalse(GLStateManager.glIsEnabled(GL11.GL_BLEND));
        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND));
        assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(GL11.GL_ZERO, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        assertEquals(GL11.GL_GREATER, GLStateManager.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));
        assertEquals(1.0F, GLStateManager.getColor().getRed(), 0.0F);
        assertEquals(0, driverTextureBinding(0));
    }

    private static void assertMutated(int texture) {
        assertFalse(GLStateManager.glIsEnabled(GL11.GL_CULL_FACE));
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        assertTrue(GLStateManager.glIsEnabled(GL11.GL_BLEND));
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
        assertEquals(GL11.GL_DST_COLOR, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(GL11.GL_ZERO, GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));
        assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
        assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        assertEquals(GL11.GL_DST_COLOR, GLStateManager.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertFalse(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        assertEquals(GL11.GL_ALWAYS, GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        assertEquals(GL11.GL_GEQUAL, GLStateManager.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));
        assertEquals(0.25F, GLStateManager.getColor().getRed(), 0.0F);
        assertEquals(0.5F, GLStateManager.getColor().getAlpha(), 0.0F);
        assertEquals(texture, driverTextureBinding(0));
        assertEquals(texture, GLStateManager.getBoundTextureForServerState(0));
    }

    @Test
    void retainedDeltaReappliesAfterPop() {
        final int texture = GL11.glGenTextures();
        final RetainedState retained = new RetainedState();
        try {
            baseline();
            final int d = GLStateManager.pushState(ALL);
            mutate(texture);
            GLStateManager.retainModifiedState(d, retained);
            GLStateManager.popStateTo(d);
            assertBaseline();

            GLStateManager.applyRetainedState(retained);
            assertMutated(texture);

            baseline();
            assertBaseline();
            GLStateManager.applyRetainedState(retained);
            assertMutated(texture);
        } finally {
            baseline();
            GL11.glDeleteTextures(texture);
        }
    }

    @Test
    void appliedDeltaIsRestoredByAnEnclosingPush() {
        final int texture = GL11.glGenTextures();
        final RetainedState retained = new RetainedState();
        try {
            baseline();
            final int d = GLStateManager.pushState(ALL);
            mutate(texture);
            GLStateManager.retainModifiedState(d, retained);
            GLStateManager.popStateTo(d);

            final int outer = GLStateManager.pushState(ALL);
            GLStateManager.applyRetainedState(retained);
            assertMutated(texture);
            GLStateManager.popStateTo(outer);
            assertBaseline();
        } finally {
            baseline();
            GL11.glDeleteTextures(texture);
        }
    }

    @Test
    void statesRestoredByANestedPushAreNotRetained() {
        final RetainedState retained = new RetainedState();
        try {
            baseline();
            final int d = GLStateManager.pushState(ALL);
            GLStateManager.enableBlend();
            final int nested = GLStateManager.pushState(ALL);
            GLStateManager.disableCull();
            GLStateManager.popStateTo(nested);
            GLStateManager.retainModifiedState(d, retained);
            GLStateManager.popStateTo(d);

            GLStateManager.applyRetainedState(retained);
            assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
            assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_CULL_FACE));
        } finally {
            baseline();
        }
    }

    @Test
    void onlyDifferingStateIsReissued() {
        final int texture = GL11.glGenTextures();
        final RetainedState retained = new RetainedState();
        try {
            baseline();
            final int d = GLStateManager.pushState(ALL);
            mutate(texture);
            GLStateManager.retainModifiedState(d, retained);
            GLStateManager.popStateTo(d);
            GLStateManager.applyRetainedState(retained);

            final GLContextState glCtx = GLStateManager.ctx();
            final long backendCalls = GLStateManager.attribBackendCalls;
            final int colorGen = glCtx.colorGeneration;
            final int fragmentGen = glCtx.fragmentGeneration;
            final int batchGen = glCtx.batchStateGeneration;
            GLStateManager.applyRetainedState(retained);
            assertEquals(backendCalls, GLStateManager.attribBackendCalls);
            assertEquals(colorGen, glCtx.colorGeneration);
            assertEquals(fragmentGen, glCtx.fragmentGeneration);
            assertEquals(batchGen, glCtx.batchStateGeneration);

            GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
            GLStateManager.applyRetainedState(retained);
            assertEquals(backendCalls + 3, GLStateManager.attribBackendCalls);
            assertMutated(texture);
        } finally {
            baseline();
            GL11.glDeleteTextures(texture);
        }
    }

    @Test
    void aSecondCaptureInvalidatesTheFirst() {
        final RetainedState first = new RetainedState();
        final RetainedState second = new RetainedState();
        try {
            baseline();
            int d = GLStateManager.pushState(ALL);
            GLStateManager.enableBlend();
            GLStateManager.retainModifiedState(d, first);
            GLStateManager.popStateTo(d);

            d = GLStateManager.pushState(ALL);
            GLStateManager.disableCull();
            GLStateManager.retainModifiedState(d, second);
            GLStateManager.popStateTo(d);

            GLStateManager.applyRetainedState(first);
            assertFalse(GL11.glIsEnabled(GL11.GL_BLEND));
            assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
            GLStateManager.applyRetainedState(second);
            assertFalse(GL11.glIsEnabled(GL11.GL_BLEND));
            assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        } finally {
            baseline();
        }
    }

    @Test
    void writesInAnOuterPushAreNotRetained() {
        final RetainedState retained = new RetainedState();
        try {
            baseline();
            GLStateManager.glClearColor(0.25F, 0.5F, 0.75F, 1.0F);
            final int outer = GLStateManager.pushState(ALL);
            GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            final int inner = GLStateManager.pushState(ALL);
            GLStateManager.enableBlend();
            GLStateManager.retainModifiedState(inner, retained);
            GLStateManager.popStateTo(outer);

            GLStateManager.applyRetainedState(retained);
            assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
            assertEquals(0.25F, GLStateManager.getClearColor().getRed(), 0.0F);
            assertEquals(1.0F, GLStateManager.getClearColor().getAlpha(), 0.0F);
        } finally {
            baseline();
            GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    @Test
    void aDeletedProgramIsNotRebound() {
        final RetainedState retained = new RetainedState();
        final int program = GLSM_StateSet_GLTest.linkTrivialProgram();
        try {
            baseline();
            final int d = GLStateManager.pushState(ALL);
            GLStateManager.glUseProgram(program);
            GLStateManager.retainModifiedState(d, retained);
            GLStateManager.popStateTo(d);
            GLStateManager.glUseProgram(0);
            GLStateManager.glDeleteProgram(program);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {}

            GLStateManager.applyRetainedState(retained);
            assertEquals(0, GLStateManager.getActiveProgram());
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glUseProgram(0);
            baseline();
        }
    }
}
