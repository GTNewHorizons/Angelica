package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import org.lwjgl.opengl.GL11;

/**
 * The state a vanilla glint block leaves behind, for callers that skip the block. GLSM is only called where its effective state differs.
 */
public final class SkippedGlintBlock {

    private static final BlendState blend = new BlendState();

    private SkippedGlintBlock() {}

    public static void applyArmorExitState() {
        applyExitState(1.0F, 1.0F, 1.0F, 1.0F, GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_SRC_COLOR, GL11.GL_ONE, true, true);
    }

    public static void applyHeldItemExitState() {
        applyItemExitState(GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void applyDroppedItemExitState() {
        applyItemExitState(GL11.GL_SRC_COLOR, GL11.GL_ONE);
    }

    private static void applyItemExitState(int srcAlpha, int dstAlpha) {
        applyExitState(0.5F * 0.76F, 0.25F * 0.76F, 0.8F * 0.76F, 1.0F, GL11.GL_SRC_COLOR, GL11.GL_ONE, srcAlpha, dstAlpha, false, false);
    }

    private static void applyExitState(float red, float green, float blue, float alpha, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, boolean resetTextureMatrix, boolean restoreDepthMask) {
        final boolean recording = GLStateManager.isExitCaptureActive();
        final Color4 color = GLSMCurrentAttribs.getColor();
        if (recording || color.getRed() != red || color.getGreen() != green || color.getBlue() != blue || color.getAlpha() != alpha) {
            GLSMCurrentAttribs.glColor4f(red, green, blue, alpha);
        }
        if (resetTextureMatrix && (recording || !MatrixHelper.isIdentity(GLSMTextures.getTextures().getTextureUnitMatrix(0)))) {
            GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
            GLSMMatrices.glLoadIdentity();
        }
        if (recording || GLSMMatrices.getMatrixMode().getMode() != GL11.GL_MODELVIEW) GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        if (restoreDepthMask && (recording || !GLSMDepthStencilAlpha.isEffectiveDepthMaskEnabled())) GLSMDepthStencilAlpha.glDepthMask(true);
        if (recording || !GLSMLighting.getLightingState().isEnabled()) GLSMEnable.glEnable(GL11.GL_LIGHTING);
        GLSMBlend.getEffectiveBlendState(blend);
        if (recording || GLSMBlend.getBlendState().isFuncUnknown() || blend.getSrcRgb() != srcRgb || blend.getDstRgb() != dstRgb || blend.getSrcAlpha() != srcAlpha || blend.getDstAlpha() != dstAlpha) {
            GLSMBlend.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
        if (recording || GLSMBlend.isEffectiveBlendEnabled()) GLSMEnable.glDisable(GL11.GL_BLEND);
        if (recording || GLSMDepthStencilAlpha.getDepthState().getFunc() != GL11.GL_LEQUAL) GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
    }
}
