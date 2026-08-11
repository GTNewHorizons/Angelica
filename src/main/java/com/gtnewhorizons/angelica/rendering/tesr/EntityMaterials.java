package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import org.lwjgl.opengl.GL11;

public final class EntityMaterials {

    static final TesrMaterial CUTOUT = TesrMaterial.builder().cutout(0.1f).stream().build();
    static final TesrMaterial CUTOUT_HALF = TesrMaterial.builder().cutout(0.5f).stream().build();
    static final TesrMaterial SOLID = TesrMaterial.builder().stream().build();
    static final TesrMaterial TRANSLUCENT = TesrMaterial.builder().translucent().cutout(1f / 255f).noDepthWrite().stream().build();
    static final TesrMaterial TRANSLUCENT_DEPTH_WRITE = TesrMaterial.builder().translucent().cutout(1f / 255f).stream().build();
    static final TesrMaterial ADDITIVE = TesrMaterial.builder().additive().stream().build();
    static final TesrMaterial ADDITIVE_NO_DEPTH_WRITE = TesrMaterial.builder().additive().noDepthWrite().stream().build();
    static final TesrMaterial ADDITIVE_ALPHA = TesrMaterial.builder().additiveAlpha().stream().build();
    static final TesrMaterial ADDITIVE_ALPHA_NO_DEPTH_WRITE = TesrMaterial.builder().additiveAlpha().noDepthWrite().stream().build();
    static final TesrMaterial OVERLAY = TesrMaterial.builder().translucent().depthEqual().stream().build();
    static final TesrMaterial GLINT = TesrMaterial.builder().glint().depthEqual().noDepthWrite().unlit().stream()
        .special(TesrMaterial.SpecialRender.GLINT).build();

    private EntityMaterials() {}

    static TesrMaterial fromCurrentState() {
        final boolean textured = GLStateManager.getTextures().getTextureUnitStates(0).isEnabled();
        final boolean texAnimated = textured && !MatrixHelper.isIdentity(GLStateManager.getTextures().getTextureUnitMatrix(0));
        final BlendState blend = GLStateManager.getBlendState();
        return fromState(textured, texAnimated,
            GLStateManager.getBlendMode().isEnabled(), blend.getSrcRgb(), blend.getDstRgb(),
            GLStateManager.getAlphaTest().isEnabled(),
            GLStateManager.getAlphaState().getFunction(), GLStateManager.getAlphaState().getReference(),
            GLStateManager.getDepthState().getFunc(), GLStateManager.getDepthState().isEnabled());
    }

    static TesrMaterial fromState(boolean textured, boolean blend, int srcRgb, int dstRgb, boolean alphaTest, int alphaFunc, float alphaRef, int depthFunc, boolean depthMask) {
        return fromState(textured, false, blend, srcRgb, dstRgb, alphaTest, alphaFunc, alphaRef, depthFunc, depthMask);
    }

    static TesrMaterial fromState(boolean textured, boolean texAnimated, boolean blend, int srcRgb, int dstRgb, boolean alphaTest, int alphaFunc, float alphaRef, int depthFunc, boolean depthMask) {
        if (!textured) {
            if (blend && depthFunc == GL11.GL_EQUAL && srcRgb == GL11.GL_SRC_ALPHA && dstRgb == GL11.GL_ONE_MINUS_SRC_ALPHA) {
                return OVERLAY;
            }
            return null;
        }
        if (texAnimated) {
            if (blend && !depthMask && depthFunc == GL11.GL_EQUAL && srcRgb == GL11.GL_SRC_COLOR && dstRgb == GL11.GL_ONE) {
                return GLINT;
            }
            if (blend && (depthFunc == GL11.GL_LEQUAL || depthFunc == GL11.GL_LESS)) {
                if (srcRgb == GL11.GL_ONE && dstRgb == GL11.GL_ONE) {
                    return depthMask ? ADDITIVE : ADDITIVE_NO_DEPTH_WRITE;
                }
                if (srcRgb == GL11.GL_SRC_ALPHA && dstRgb == GL11.GL_ONE) {
                    return depthMask ? ADDITIVE_ALPHA : ADDITIVE_ALPHA_NO_DEPTH_WRITE;
                }
            }
            return null;
        }
        if (depthFunc != GL11.GL_LEQUAL && depthFunc != GL11.GL_LESS) {
            return null;
        }
        if (!blend) {
            if (!alphaTest) return SOLID;
            if (alphaFunc == GL11.GL_GREATER && Math.abs(alphaRef - 0.1f) < 1e-4f) return CUTOUT;
            if (alphaFunc == GL11.GL_GREATER && Math.abs(alphaRef - 0.5f) < 1e-4f) return CUTOUT_HALF;
            return null;
        }
        if (srcRgb == GL11.GL_SRC_ALPHA && dstRgb == GL11.GL_ONE_MINUS_SRC_ALPHA) {
            return depthMask ? TRANSLUCENT_DEPTH_WRITE : TRANSLUCENT;
        }
        if (srcRgb == GL11.GL_ONE && dstRgb == GL11.GL_ONE) {
            return depthMask ? ADDITIVE : ADDITIVE_NO_DEPTH_WRITE;
        }
        if (srcRgb == GL11.GL_SRC_ALPHA && dstRgb == GL11.GL_ONE) {
            return depthMask ? ADDITIVE_ALPHA : ADDITIVE_ALPHA_NO_DEPTH_WRITE;
        }
        return null;
    }
}
