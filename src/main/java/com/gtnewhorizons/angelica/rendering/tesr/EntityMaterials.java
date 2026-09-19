package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
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
    static final TesrMaterial ADDITIVE_CUTOUT = TesrMaterial.builder().additive().cutout(0.1f).stream().build();
    static final TesrMaterial ADDITIVE_CUTOUT_NO_DEPTH_WRITE = TesrMaterial.builder().additive().cutout(0.1f).noDepthWrite().stream().build();
    static final TesrMaterial ADDITIVE_ALPHA = TesrMaterial.builder().additiveAlpha().stream().build();
    static final TesrMaterial ADDITIVE_ALPHA_NO_DEPTH_WRITE = TesrMaterial.builder().additiveAlpha().noDepthWrite().stream().build();
    static final TesrMaterial ADDITIVE_ALPHA_CUTOUT = TesrMaterial.builder().additiveAlpha().cutout(0.1f).stream().build();
    static final TesrMaterial ADDITIVE_ALPHA_CUTOUT_NO_DEPTH_WRITE = TesrMaterial.builder().additiveAlpha().cutout(0.1f).noDepthWrite().stream().build();
    static final TesrMaterial SHADOW = TesrMaterial.builder().translucent().cutout(0.1f).noDepthWrite().unlit().stream().build();
    public static final TesrMaterial DROPPED_ITEM_CUTOUT = TesrMaterial.builder().cutout(0.1f).stream().unfilteredAtlas().build();
    public static final TesrMaterial DROPPED_ITEM_TRANSLUCENT = TesrMaterial.builder().translucent().cutout(0.1f).unfilteredAtlas().stream().build();
    public static final TesrMaterial HELD_BLOCK_CUTOUT = TesrMaterial.builder().cutout(0.1f).stream().build();
    public static final TesrMaterial HELD_BLOCK_TRANSLUCENT = TesrMaterial.builder().translucent().cutout(0.1f).stream().build();
    static final TesrMaterial OVERLAY = TesrMaterial.builder().translucent().depthEqual().stream().build();
    public static final TesrMaterial GLINT = TesrMaterial.builder().glint().depthEqual().noDepthWrite().unlit().stream()
        .special(TesrMaterial.SpecialRender.GLINT).build();

    private static final BlendState effectiveBlend = new BlendState();
    private static final AlphaState effectiveAlpha = new AlphaState();

    private EntityMaterials() {}

    static TesrMaterial fromCurrentState(boolean texIdentity) {
        final boolean textured = GLStateManager.getTextures().getTextureUnitStates(0).isEnabled();
        final BlendState blend = GLStateManager.getEffectiveBlendState(effectiveBlend);
        final AlphaState alpha = GLStateManager.getEffectiveAlphaState(effectiveAlpha);
        return fromState(textured, textured && !texIdentity, GLStateManager.isEffectiveBlendEnabled(), blend.getSrcRgb(), blend.getDstRgb(), GLStateManager.isEffectiveAlphaTestEnabled(), alpha.getFunction(), alpha.getReference(), GLStateManager.getDepthState().getFunc(),GLStateManager.isEffectiveDepthMaskEnabled());
    }

    public static TesrMaterial itemFromCurrentState(boolean unfilteredAtlas) {
        final BlendState blend = GLStateManager.getEffectiveBlendState(effectiveBlend);
        final AlphaState alpha = GLStateManager.getEffectiveAlphaState(effectiveAlpha);
        return itemFromState(unfilteredAtlas, GLStateManager.isEffectiveBlendEnabled(), blend.getSrcRgb(), blend.getDstRgb(), GLStateManager.isEffectiveAlphaTestEnabled(), alpha.getFunction(), alpha.getReference(), GLStateManager.getDepthState().getFunc(),GLStateManager.isEffectiveDepthMaskEnabled());
    }

    static TesrMaterial itemFromState(boolean unfilteredAtlas, boolean blend, int srcRgb, int dstRgb, boolean alphaTest, int alphaFunc, float alphaRef, int depthFunc, boolean depthMask) {
        if (depthFunc != GL11.GL_LEQUAL && depthFunc != GL11.GL_LESS) return null;
        if (!depthMask) return null;
        if (!alphaTest) return null;
        if (alphaFunc != GL11.GL_GREATER || Math.abs(alphaRef - 0.1f) > 1.0e-4f) return null;
        if (blend) {
            if (srcRgb != GL11.GL_SRC_ALPHA || dstRgb != GL11.GL_ONE_MINUS_SRC_ALPHA) return null;
            return unfilteredAtlas ? DROPPED_ITEM_TRANSLUCENT : HELD_BLOCK_TRANSLUCENT;
        }
        return unfilteredAtlas ? DROPPED_ITEM_CUTOUT : HELD_BLOCK_CUTOUT;
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
                return additiveFromState(srcRgb, dstRgb, alphaTest, alphaFunc, alphaRef, depthMask);
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
        return additiveFromState(srcRgb, dstRgb, alphaTest, alphaFunc, alphaRef, depthMask);
    }

    private static TesrMaterial additiveFromState(int srcRgb, int dstRgb, boolean alphaTest, int alphaFunc, float alphaRef, boolean depthMask) {
        final boolean cutout;
        if (!alphaTest) {
            cutout = false;
        } else if (alphaFunc == GL11.GL_GREATER && Math.abs(alphaRef - 0.1f) < 1e-4f) {
            cutout = true;
        } else {
            return null;
        }
        if (srcRgb == GL11.GL_ONE && dstRgb == GL11.GL_ONE) {
            if (cutout) return depthMask ? ADDITIVE_CUTOUT : ADDITIVE_CUTOUT_NO_DEPTH_WRITE;
            return depthMask ? ADDITIVE : ADDITIVE_NO_DEPTH_WRITE;
        }
        if (srcRgb == GL11.GL_SRC_ALPHA && dstRgb == GL11.GL_ONE) {
            if (cutout) return depthMask ? ADDITIVE_ALPHA_CUTOUT : ADDITIVE_ALPHA_CUTOUT_NO_DEPTH_WRITE;
            return depthMask ? ADDITIVE_ALPHA : ADDITIVE_ALPHA_NO_DEPTH_WRITE;
        }
        return null;
    }
}
