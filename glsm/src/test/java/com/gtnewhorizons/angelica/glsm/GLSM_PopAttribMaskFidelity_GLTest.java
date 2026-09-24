package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyIsEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class GLSM_PopAttribMaskFidelity_GLTest {

    private static int scanClipPlaneMask() {
        final BooleanStateStack[] planes = GLStateManager.getClipPlaneStates();
        int mask = 0;
        for (int i = 0; i < planes.length; i++) {
            if (planes[i].isEnabled()) mask |= (1 << i);
        }
        return mask;
    }

    private static void verifyClipPlaneMask(String message) {
        final int scanned = scanClipPlaneMask();
        assertEquals(scanned, GLStateManager.ctx().clipPlaneEnabledMask, message + " - mask mismatch");
        assertEquals(scanned != 0, GLStateManager.anyClipPlaneEnabled(), message + " - anyClipPlaneEnabled mismatch");
    }

    @Test
    void colorBufferBitDoesNotRestoreCull() {
        try {
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Initial");

            GLStateManager.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLStateManager.glEnable(GL11.GL_CULL_FACE);
            GLStateManager.glPopAttrib();

            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - Not a GL_COLOR_BUFFER_BIT member, must stay toggled");
        } finally {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Test
    void enableBitRestoresCull() {
        try {
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Initial");

            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLStateManager.glEnable(GL11.GL_CULL_FACE);
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - After enable");
            GLStateManager.glPopAttrib();

            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - GL_ENABLE_BIT member, must restore");
        } finally {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Test
    void innerNonMemberPopKeepsChange_OuterMemberPopRestores() {
        try {
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Initial");

            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLStateManager.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLStateManager.glEnable(GL11.GL_CULL_FACE);
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - After enable inside inner bracket");

            GLStateManager.glPopAttrib();
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - Inner pop is not a member, must stay toggled");

            GLStateManager.glPopAttrib();
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Outer pop is a member, must restore to pre-inner value");
        } finally {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Test
    void modifyBeforeInnerPush_SurvivesInnerNonMemberPop() {
        try {
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Initial");

            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLStateManager.glEnable(GL11.GL_CULL_FACE);
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - After enable at outer depth");

            GLStateManager.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLStateManager.glPopAttrib();
            verifyIsEnabled(GL11.GL_CULL_FACE, true, "Cull - Inner pop is not a member, must stay toggled");

            GLStateManager.glPopAttrib();
            verifyIsEnabled(GL11.GL_CULL_FACE, false, "Cull - Outer pop is a member, must restore");
        } finally {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
        }
    }

    @Test
    void twoModifiesAtOneDepthRestoreOnce() {
        try {
            verifyIsEnabled(GL11.GL_BLEND, false, "Blend - Initial");

            GLStateManager.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glDisable(GL11.GL_BLEND);
            GLStateManager.glEnable(GL11.GL_BLEND);
            verifyIsEnabled(GL11.GL_BLEND, true, "Blend - After multiple toggles at one depth");

            GLStateManager.glPopAttrib();
            verifyIsEnabled(GL11.GL_BLEND, false, "Blend - GL_COLOR_BUFFER_BIT member, must restore original");
        } finally {
            GLStateManager.glDisable(GL11.GL_BLEND);
        }
    }

    @Test
    void lightingBitRestoresLightingAndLightEnables() {
        try {
            verifyIsEnabled(GL11.GL_LIGHTING, false, "Lighting - Initial");
            verifyIsEnabled(GL11.GL_LIGHT0, false, "Light0 - Initial");

            GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
            GLStateManager.glEnable(GL11.GL_LIGHTING);
            GLStateManager.glEnable(GL11.GL_LIGHT0);
            verifyIsEnabled(GL11.GL_LIGHTING, true, "Lighting - After enable");
            verifyIsEnabled(GL11.GL_LIGHT0, true, "Light0 - After enable");

            GLStateManager.glPopAttrib();
            verifyIsEnabled(GL11.GL_LIGHTING, false, "Lighting - GL_LIGHTING_BIT member, must restore");
            verifyIsEnabled(GL11.GL_LIGHT0, false, "Light0 - GL_LIGHTING_BIT member, must restore");
        } finally {
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glDisable(GL11.GL_LIGHT0);
        }
    }

    @Test
    void enableDisableTracksClipPlaneMask() {
        try {
            verifyClipPlaneMask("Initial");

            GLStateManager.glEnable(GL11.GL_CLIP_PLANE0);
            verifyIsEnabled(GL11.GL_CLIP_PLANE0, true, "Clip0 - After enable");
            verifyClipPlaneMask("After enable");

            GLStateManager.glDisable(GL11.GL_CLIP_PLANE0);
            verifyIsEnabled(GL11.GL_CLIP_PLANE0, false, "Clip0 - After disable");
            verifyClipPlaneMask("After disable");
        } finally {
            GLStateManager.glDisable(GL11.GL_CLIP_PLANE0);
        }
    }

    @Test
    void transformBitPopRestoresClipPlaneAndMask() {
        try {
            verifyClipPlaneMask("Initial");

            GLStateManager.glPushAttrib(GL11.GL_TRANSFORM_BIT);
            GLStateManager.glEnable(GL11.GL_CLIP_PLANE1);
            verifyIsEnabled(GL11.GL_CLIP_PLANE1, true, "Clip1 - After enable inside GL_TRANSFORM_BIT");
            verifyClipPlaneMask("After enable inside GL_TRANSFORM_BIT");

            GLStateManager.glPopAttrib();
            verifyIsEnabled(GL11.GL_CLIP_PLANE1, false, "Clip1 - GL_TRANSFORM_BIT member, must restore");
            verifyClipPlaneMask("After GL_TRANSFORM_BIT pop");
        } finally {
            GLStateManager.glDisable(GL11.GL_CLIP_PLANE1);
        }
    }

    @Test
    void colorBufferBitPopDoesNotRestoreClipPlaneButMaskStaysFaithful() {
        try {
            verifyClipPlaneMask("Initial");

            GLStateManager.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
            GLStateManager.glEnable(GL11.GL_CLIP_PLANE2);
            verifyIsEnabled(GL11.GL_CLIP_PLANE2, true, "Clip2 - After enable inside GL_COLOR_BUFFER_BIT");
            verifyClipPlaneMask("After enable inside GL_COLOR_BUFFER_BIT");

            GLStateManager.glPopAttrib();
            verifyIsEnabled(GL11.GL_CLIP_PLANE2, true, "Clip2 - Not a GL_COLOR_BUFFER_BIT member, must stay toggled");
            verifyClipPlaneMask("After GL_COLOR_BUFFER_BIT pop");
        } finally {
            GLStateManager.glDisable(GL11.GL_CLIP_PLANE2);
        }
    }
}
