package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
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

    void captureLive(GLContextState ctx) {
        depthTest = ctx.depthTest.isEnabled();
        depthFunc = ctx.depthState.getFunc();
        depthMask = ctx.depthState.isEffectiveMaskEnabled();
        final ColorMask colorMask = ctx.colorMask.readEffective(colorMaskScratch);
        colorRed = colorMask.red;
        colorGreen = colorMask.green;
        colorBlue = colorMask.blue;
        colorAlpha = colorMask.alpha;
        final PolygonState polygon = ctx.polygonState;
        cull = ctx.cullState.isEnabled();
        cullFace = polygon.getCullFaceMode();
        frontFace = polygon.getFrontFace();
        polygonOffset = ctx.polygonOffsetFillState.isEnabled();
        offsetFactor = polygon.getOffsetFactor();
        offsetUnits = polygon.getOffsetUnits();
        alphaRef = ctx.alphaState.getReference();
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
        if (depthTest) GLSMDepthStencilAlpha.enableDepthTest(); else GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(depthFunc);
        GLSMDepthStencilAlpha.glDepthMask(depthMask);
        GLSMBlend.glColorMask(colorRed, colorGreen, colorBlue, colorAlpha);
        if (cull) GLSMRasterizer.enableCull(); else GLSMRasterizer.disableCull();
        GLSMRasterizer.glCullFace(cullFace);
        GLSMRasterizer.glFrontFace(frontFace);
        if (polygonOffset) GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL); else GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(offsetFactor, offsetUnits);
    }
}
