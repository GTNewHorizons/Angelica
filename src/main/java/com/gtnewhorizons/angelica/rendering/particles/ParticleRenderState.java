package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import org.lwjgl.opengl.GL11;

public final class ParticleRenderState {

    private final BlendState scratch = new BlendState();

    public int texture;
    public boolean blend;
    public int blendSrc;
    public int blendDst;
    public int blendSrcAlpha;
    public int blendDstAlpha;
    public boolean depthMask;

    public void sample() {
        texture = GLStateManager.getBoundTextureForServerState();
        blend = GLStateManager.isEffectiveBlendEnabled();
        GLStateManager.getEffectiveBlendState(scratch);
        blendSrc = scratch.getSrcRgb();
        blendDst = scratch.getDstRgb();
        blendSrcAlpha = scratch.getSrcAlpha();
        blendDstAlpha = scratch.getDstAlpha();
        depthMask = GLStateManager.isEffectiveDepthMaskEnabled();
    }

    public void set(ParticleRenderState other) {
        texture = other.texture;
        blend = other.blend;
        blendSrc = other.blendSrc;
        blendDst = other.blendDst;
        blendSrcAlpha = other.blendSrcAlpha;
        blendDstAlpha = other.blendDstAlpha;
        depthMask = other.depthMask;
    }

    public boolean matches(ParticleRenderState other) {
        return texture == other.texture && blend == other.blend && blendSrc == other.blendSrc && blendDst == other.blendDst && blendSrcAlpha == other.blendSrcAlpha && blendDstAlpha == other.blendDstAlpha && depthMask == other.depthMask;
    }

    public void apply() {
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (blend) {
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha);
        } else {
            GLStateManager.glDisable(GL11.GL_BLEND);
        }
        GLStateManager.glDepthMask(depthMask);
    }
}
