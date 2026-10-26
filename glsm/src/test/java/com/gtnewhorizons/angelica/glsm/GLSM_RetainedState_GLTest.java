package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.stacks.RetainedState;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.drainErrors;
import static com.gtnewhorizons.angelica.glsm.GlTestSupport.driverTextureBinding;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_RetainedState_GLTest {

    private static final StateSet ALL = StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS);

    private static void baseline() {
        GLSMRasterizer.enableCull();
        GLSMBlend.disableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GLSMCurrentAttribs.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private static void mutate(int texture) {
        GLSMRasterizer.disableCull();
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE);
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GEQUAL, 0.5F);
        GLSMCurrentAttribs.glColor4f(0.25F, 0.5F, 0.75F, 0.5F);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static void assertBaseline() {
        assertTrue(GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE));
        assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        assertFalse(GLSMEnable.glIsEnabled(GL11.GL_BLEND));
        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND));
        assertEquals(GL11.GL_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(GL11.GL_ZERO, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        assertEquals(GL11.GL_GREATER, GLSMQueries.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));
        assertEquals(1.0F, GLSMCurrentAttribs.getColor().getRed(), 0.0F);
        assertEquals(0, driverTextureBinding(0));
    }

    private static void assertMutated(int texture) {
        assertFalse(GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE));
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE));
        assertTrue(GLSMEnable.glIsEnabled(GL11.GL_BLEND));
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
        assertEquals(GL11.GL_DST_COLOR, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(GL11.GL_ZERO, GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));
        assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
        assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        assertEquals(GL11.GL_DST_COLOR, GLSMQueries.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertFalse(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        assertEquals(GL11.GL_ALWAYS, GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        assertEquals(GL11.GL_GEQUAL, GLSMQueries.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));
        assertEquals(0.25F, GLSMCurrentAttribs.getColor().getRed(), 0.0F);
        assertEquals(0.5F, GLSMCurrentAttribs.getColor().getAlpha(), 0.0F);
        assertEquals(texture, driverTextureBinding(0));
        assertEquals(texture, GLSMTextures.getBoundTextureForServerState(0));
    }

    @Test
    void retainedDeltaReappliesAfterPop() {
        final int texture = GL11.glGenTextures();
        final RetainedState retained = new RetainedState();
        try {
            baseline();
            final int d = GLSMAttribStack.pushState(ALL);
            mutate(texture);
            GLSMAttribStack.retainModifiedState(d, retained);
            GLSMAttribStack.popStateTo(d);
            assertBaseline();

            GLSMAttribStack.applyRetainedState(retained);
            assertMutated(texture);

            baseline();
            assertBaseline();
            GLSMAttribStack.applyRetainedState(retained);
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
            final int d = GLSMAttribStack.pushState(ALL);
            mutate(texture);
            GLSMAttribStack.retainModifiedState(d, retained);
            GLSMAttribStack.popStateTo(d);

            final int outer = GLSMAttribStack.pushState(ALL);
            GLSMAttribStack.applyRetainedState(retained);
            assertMutated(texture);
            GLSMAttribStack.popStateTo(outer);
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
            final int d = GLSMAttribStack.pushState(ALL);
            GLSMBlend.enableBlend();
            final int nested = GLSMAttribStack.pushState(ALL);
            GLSMRasterizer.disableCull();
            GLSMAttribStack.popStateTo(nested);
            GLSMAttribStack.retainModifiedState(d, retained);
            GLSMAttribStack.popStateTo(d);

            GLSMAttribStack.applyRetainedState(retained);
            assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
            assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
            assertTrue(GLSMEnable.glIsEnabled(GL11.GL_CULL_FACE));
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
            final int d = GLSMAttribStack.pushState(ALL);
            mutate(texture);
            GLSMAttribStack.retainModifiedState(d, retained);
            GLSMAttribStack.popStateTo(d);
            GLSMAttribStack.applyRetainedState(retained);

            final GLContextState glCtx = GLStateManager.ctx();
            final long backendCalls = GLSMAttribStack.attribBackendCalls;
            final int colorGen = glCtx.colorGeneration;
            final int fragmentGen = glCtx.fragmentGeneration;
            final int batchGen = glCtx.batchStateGeneration;
            GLSMAttribStack.applyRetainedState(retained);
            assertEquals(backendCalls, GLSMAttribStack.attribBackendCalls);
            assertEquals(colorGen, glCtx.colorGeneration);
            assertEquals(fragmentGen, glCtx.fragmentGeneration);
            assertEquals(batchGen, glCtx.batchStateGeneration);

            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
            GLSMAttribStack.applyRetainedState(retained);
            assertEquals(backendCalls + 3, GLSMAttribStack.attribBackendCalls);
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
            int d = GLSMAttribStack.pushState(ALL);
            GLSMBlend.enableBlend();
            GLSMAttribStack.retainModifiedState(d, first);
            GLSMAttribStack.popStateTo(d);

            d = GLSMAttribStack.pushState(ALL);
            GLSMRasterizer.disableCull();
            GLSMAttribStack.retainModifiedState(d, second);
            GLSMAttribStack.popStateTo(d);

            GLSMAttribStack.applyRetainedState(first);
            assertFalse(GL11.glIsEnabled(GL11.GL_BLEND));
            assertTrue(GL11.glIsEnabled(GL11.GL_CULL_FACE));
            GLSMAttribStack.applyRetainedState(second);
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
            GLSMBlend.glClearColor(0.25F, 0.5F, 0.75F, 1.0F);
            final int outer = GLSMAttribStack.pushState(ALL);
            GLSMBlend.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            final int inner = GLSMAttribStack.pushState(ALL);
            GLSMBlend.enableBlend();
            GLSMAttribStack.retainModifiedState(inner, retained);
            GLSMAttribStack.popStateTo(outer);

            GLSMAttribStack.applyRetainedState(retained);
            assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
            assertEquals(0.25F, GLSMBlend.getClearColor().getRed(), 0.0F);
            assertEquals(1.0F, GLSMBlend.getClearColor().getAlpha(), 0.0F);
        } finally {
            baseline();
            GLSMBlend.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    @Test
    void aDeletedProgramIsNotRebound() {
        final RetainedState retained = new RetainedState();
        final int program = GlTestSupport.program(GlTestSupport.TRIVIAL_VS, GlTestSupport.TRIVIAL_FS);
        try {
            baseline();
            final int d = GLSMAttribStack.pushState(ALL);
            GLSMPrograms.glUseProgram(program);
            GLSMAttribStack.retainModifiedState(d, retained);
            GLSMAttribStack.popStateTo(d);
            GLSMPrograms.glUseProgram(0);
            GLSMPrograms.glDeleteProgram(program);
            drainErrors();

            GLSMAttribStack.applyRetainedState(retained);
            assertEquals(0, GLSMPrograms.getActiveProgram());
        } finally {
            GLSMPrograms.glUseProgram(0);
            baseline();
        }
    }
}
