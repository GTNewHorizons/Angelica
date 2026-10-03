package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.states.PolygonState;
import org.lwjgl.opengl.GL11;

/**
 * Drawing text picks up settings from whoever asked for it, such as depth testing, culling, polygon offset and
 * alpha cutoff. Text is sometimes drawn later than requested, so these are saved with it to make the late draw
 * look the same as an immediate one.
 */
final class TextDrawState {
    private final ColorMask colorMaskScratch = new ColorMask();

    boolean depthTest;
    int depthFunc;
    boolean depthMask;
    boolean colorRed;
    boolean colorGreen;
    boolean colorBlue;
    boolean colorAlpha;
    boolean cull;
    int cullFace;
    int frontFace;
    boolean polygonOffset;
    float offsetFactor;
    float offsetUnits;
    float alphaRef;

    void captureLive() {
        depthTest = GLStateManager.getDepthTest().isEnabled();
        depthFunc = GLStateManager.getDepthState().getFunc();
        depthMask = GLStateManager.isEffectiveDepthMaskEnabled();
        final ColorMask colorMask = GLStateManager.getEffectiveColorMask(colorMaskScratch);
        colorRed = colorMask.red;
        colorGreen = colorMask.green;
        colorBlue = colorMask.blue;
        colorAlpha = colorMask.alpha;
        final PolygonState polygon = GLStateManager.getPolygonState();
        cull = GLStateManager.getCullState().isEnabled();
        cullFace = polygon.getCullFaceMode();
        frontFace = polygon.getFrontFace();
        polygonOffset = GLStateManager.getPolygonOffsetFillState().isEnabled();
        offsetFactor = polygon.getOffsetFactor();
        offsetUnits = polygon.getOffsetUnits();
        alphaRef = GLStateManager.getAlphaState().getReference();
    }

    boolean sameAs(TextDrawState other) {
        return depthTest == other.depthTest
            && depthFunc == other.depthFunc
            && depthMask == other.depthMask
            && colorRed == other.colorRed
            && colorGreen == other.colorGreen
            && colorBlue == other.colorBlue
            && colorAlpha == other.colorAlpha
            && cull == other.cull
            && cullFace == other.cullFace
            && frontFace == other.frontFace
            && polygonOffset == other.polygonOffset
            && offsetFactor == other.offsetFactor
            && offsetUnits == other.offsetUnits
            && alphaRef == other.alphaRef;
    }

    void set(TextDrawState other) {
        depthTest = other.depthTest;
        depthFunc = other.depthFunc;
        depthMask = other.depthMask;
        colorRed = other.colorRed;
        colorGreen = other.colorGreen;
        colorBlue = other.colorBlue;
        colorAlpha = other.colorAlpha;
        cull = other.cull;
        cullFace = other.cullFace;
        frontFace = other.frontFace;
        polygonOffset = other.polygonOffset;
        offsetFactor = other.offsetFactor;
        offsetUnits = other.offsetUnits;
        alphaRef = other.alphaRef;
    }

    void apply() {
        if (depthTest) GLStateManager.enableDepthTest(); else GLStateManager.disableDepthTest();
        GLStateManager.glDepthFunc(depthFunc);
        GLStateManager.glDepthMask(depthMask);
        GLStateManager.glColorMask(colorRed, colorGreen, colorBlue, colorAlpha);
        if (cull) GLStateManager.enableCull(); else GLStateManager.disableCull();
        GLStateManager.glCullFace(cullFace);
        GLStateManager.glFrontFace(frontFace);
        if (polygonOffset) GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL); else GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(offsetFactor, offsetUnits);
    }
}
