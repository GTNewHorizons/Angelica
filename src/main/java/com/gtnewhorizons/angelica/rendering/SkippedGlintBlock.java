package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
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
        applyExitState(0.5F * 0.76F, 0.25F * 0.76F, 0.8F * 0.76F, 1.0F, GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, false, false);
    }

    private static void applyExitState(float red, float green, float blue, float alpha, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, boolean resetTextureMatrix, boolean restoreDepthMask) {
        final Color4 color = GLStateManager.getColor();
        if (color.getRed() != red || color.getGreen() != green || color.getBlue() != blue || color.getAlpha() != alpha) {
            GLStateManager.glColor4f(red, green, blue, alpha);
        }
        if (resetTextureMatrix && !MatrixHelper.isIdentity(GLStateManager.getTextures().getTextureUnitMatrix(0))) {
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            GLStateManager.glLoadIdentity();
        }
        if (GLStateManager.getMatrixMode().getMode() != GL11.GL_MODELVIEW) GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        if (restoreDepthMask && !GLStateManager.isEffectiveDepthMaskEnabled()) GLStateManager.glDepthMask(true);
        if (!GLStateManager.getLightingState().isEnabled()) GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.getEffectiveBlendState(blend);
        if (GLStateManager.getBlendState().isFuncUnknown() || blend.getSrcRgb() != srcRgb || blend.getDstRgb() != dstRgb || blend.getSrcAlpha() != srcAlpha || blend.getDstAlpha() != dstAlpha) {
            GLStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
        if (GLStateManager.isEffectiveBlendEnabled()) GLStateManager.glDisable(GL11.GL_BLEND);
        if (GLStateManager.getDepthState().getFunc() != GL11.GL_LEQUAL) GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
    }
}
