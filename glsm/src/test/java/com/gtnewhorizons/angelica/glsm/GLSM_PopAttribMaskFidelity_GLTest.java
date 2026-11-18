package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyIsEnabled;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;
import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class GLSM_PopAttribMaskFidelity_GLTest {

    private static int scanClipPlaneMask() {
        final BooleanStateStack[] planes = GLSMRasterizer.getClipPlaneStates();
        int mask = 0;
        for (int i = 0; i < planes.length; i++) {
            if (planes[i].isEnabled()) mask |= (1 << i);
        }
        return mask;
    }

    private static void verifyClipPlaneMask(String message) {
        final int scanned = scanClipPlaneMask();
        assertEquals(scanned, GLStateManager.ctx().clipPlaneEnabledMask, message + " - mask mismatch");
        assertEquals(scanned != 0, GLSMRasterizer.anyClipPlaneEnabled(), message + " - anyClipPlaneEnabled mismatch");
    }

    @Test
    void colorBufferBitDoesNotRestoreCull() {
        try {
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Initial");

            GLSMAttribStack.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLSMEnable.glEnable(GL11.GL_CULL_FACE);
            GLSMAttribStack.glPopAttrib();

            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - Not a GL_COLOR_BUFFER_BIT member, must stay toggled");
        } finally {
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Test
    void innerNonMemberPopKeepsChange_OuterMemberPopRestores() {
        try {
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Initial");

            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMAttribStack.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLSMEnable.glEnable(GL11.GL_CULL_FACE);
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - After enable inside inner bracket");

            GLSMAttribStack.glPopAttrib();
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - Inner pop is not a member, must stay toggled");

            GLSMAttribStack.glPopAttrib();
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Outer pop is a member, must restore to pre-inner value");
        } finally {
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Test
    void modifyBeforeInnerPush_SurvivesInnerNonMemberPop() {
        try {
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Initial");

            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMEnable.glEnable(GL11.GL_CULL_FACE);
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - After enable at outer depth");

            GLSMAttribStack.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLSMAttribStack.glPopAttrib();
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - Inner pop is not a member, must stay toggled");

            GLSMAttribStack.glPopAttrib();
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Outer pop is a member, must restore");
        } finally {
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Test
    void twoModifiesAtOneDepthRestoreOnce() {
        try {
            verifyIsEnabled(GL11.GL_BLEND, false, "Blend - Initial");

            GLSMAttribStack.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLSMEnable.glEnable(GL11.GL_BLEND);
            GLSMEnable.glDisable(GL11.GL_BLEND);
            GLSMEnable.glEnable(GL11.GL_BLEND);
            verifyIsEnabled(GL11.GL_BLEND, true, "Blend - After multiple toggles at one depth");

            GLSMAttribStack.glPopAttrib();
            verifyIsEnabled(GL11.GL_BLEND, false, "Blend - GL_COLOR_BUFFER_BIT member, must restore original");
        } finally {
            GLSMEnable.glDisable(GL11.GL_BLEND);
        }
    }

    @Test
    void lightingBitRestoresLightingAndLightEnables() {
        try {
            verifyIsEnabled(GL11.GL_LIGHTING, false, "Lighting - Initial");
            verifyIsEnabled(GL11.GL_LIGHT0, false, "Light0 - Initial");

            GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
            GLSMEnable.glEnable(GL11.GL_LIGHTING);
            GLSMEnable.glEnable(GL11.GL_LIGHT0);
            verifyIsEnabled(GL11.GL_LIGHTING, true, "Lighting - After enable");
            verifyIsEnabled(GL11.GL_LIGHT0, true, "Light0 - After enable");

            GLSMAttribStack.glPopAttrib();
            verifyIsEnabled(GL11.GL_LIGHTING, false, "Lighting - GL_LIGHTING_BIT member, must restore");
            verifyIsEnabled(GL11.GL_LIGHT0, false, "Light0 - GL_LIGHTING_BIT member, must restore");
        } finally {
            GLSMEnable.glDisable(GL11.GL_LIGHTING);
            GLSMEnable.glDisable(GL11.GL_LIGHT0);
        }
    }

    @Test
    void enableDisableTracksClipPlaneMask() {
        try {
            verifyClipPlaneMask("Initial");

            GLSMEnable.glEnable(GL11.GL_CLIP_PLANE0);
            verifyIsEnabled(GL11.GL_CLIP_PLANE0, true, "Clip0 - After enable");
            verifyClipPlaneMask("After enable");

            GLSMEnable.glDisable(GL11.GL_CLIP_PLANE0);
            verifyIsEnabled(GL11.GL_CLIP_PLANE0, false, "Clip0 - After disable");
            verifyClipPlaneMask("After disable");
        } finally {
            GLSMEnable.glDisable(GL11.GL_CLIP_PLANE0);
        }
    }

    static Stream<Arguments> clipPlanePops() {
        return Stream.of(
            Arguments.of("GL_TRANSFORM_BIT", GL11.GL_TRANSFORM_BIT, GL11.GL_CLIP_PLANE1, true),
            Arguments.of("GL_COLOR_BUFFER_BIT", GL11.GL_COLOR_BUFFER_BIT, GL11.GL_CLIP_PLANE2, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void clipPlanePops(String bitName, int bit, int plane, boolean member) {
        try {
            verifyClipPlaneMask("Initial");

            GLSMAttribStack.glPushAttrib(bit);
            GLSMEnable.glEnable(plane);
            verifyIsEnabled(plane, true, "After enable inside " + bitName);
            verifyClipPlaneMask("After enable inside " + bitName);

            GLSMAttribStack.glPopAttrib();
            verifyIsEnabled(plane, !member, member ? bitName + " member, must restore" : "Not a " + bitName + " member, must stay toggled");
            verifyClipPlaneMask("After " + bitName + " pop");
        } finally {
            GLSMEnable.glDisable(plane);
        }
    }

    @Test
    void scissorBoxFollowsScissorBitOnly() {
        final int[] boxA = { 10, 20, 100, 200 };
        final int[] boxB = { 30, 40, 50, 60 };
        try {
            GLSMRasterizer.glScissor(boxA[0], boxA[1], boxA[2], boxA[3]);
            GLSMRasterizer.disableScissorTest();
            verifyState(GL11.GL_SCISSOR_BOX, boxA, "Scissor box - Initial");
            verifyIsEnabled(GL11.GL_SCISSOR_TEST, false, "Scissor - Initial");

            GLSMAttribStack.glPushAttrib(GL11.GL_SCISSOR_BIT);
            GLSMRasterizer.enableScissorTest();
            GLSMRasterizer.glScissor(boxB[0], boxB[1], boxB[2], boxB[3]);
            GLSMAttribStack.glPopAttrib();

            verifyState(GL11.GL_SCISSOR_BOX, boxA, "Scissor box - GL_SCISSOR_BIT member, must restore");
            verifyIsEnabled(GL11.GL_SCISSOR_TEST, false, "Scissor - GL_SCISSOR_BIT member, must restore");

            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMRasterizer.enableScissorTest();
            GLSMRasterizer.glScissor(boxB[0], boxB[1], boxB[2], boxB[3]);
            GLSMAttribStack.glPopAttrib();

            verifyState(GL11.GL_SCISSOR_BOX, boxB, "Scissor box - Not a GL_ENABLE_BIT member, must stay changed");
            verifyIsEnabled(GL11.GL_SCISSOR_TEST, false, "Scissor - GL_ENABLE_BIT member, must restore");
        } finally {
            GLSMRasterizer.disableScissorTest();
            GLSMRasterizer.glScissor(0, 0, 800, 600);
        }
    }

    @Test
    void rasterizerDiscardIsNotAnEnableBitMember() {
        try {
            verifyIsEnabled(GL30.GL_RASTERIZER_DISCARD, false, "RasterizerDiscard - Initial");

            GLSMEnable.glEnable(GL30.GL_RASTERIZER_DISCARD);
            verifyIsEnabled(GL30.GL_RASTERIZER_DISCARD, true, "RasterizerDiscard - After enable");

            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMEnable.glDisable(GL30.GL_RASTERIZER_DISCARD);
            verifyIsEnabled(GL30.GL_RASTERIZER_DISCARD, false, "RasterizerDiscard - After disable inside GL_ENABLE_BIT");
            GLSMAttribStack.glPopAttrib();

            verifyIsEnabled(GL30.GL_RASTERIZER_DISCARD, false, "RasterizerDiscard - Not a GL_ENABLE_BIT member, must stay disabled");
        } finally {
            GLSMEnable.glDisable(GL30.GL_RASTERIZER_DISCARD);
        }
    }

    @Test
    void clearDepthIsClamped() {
        try {
            GLSMDepthStencilAlpha.glClearDepth(2.0);
            verifyState(GL11.GL_DEPTH_CLEAR_VALUE, 1.0f, "Depth clear value - glClearDepth(2.0) must clamp to 1.0");
        } finally {
            GLSMDepthStencilAlpha.glClearDepth(1.0);
        }
    }
}
