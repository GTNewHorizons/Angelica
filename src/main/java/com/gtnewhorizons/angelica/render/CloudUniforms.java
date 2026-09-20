package com.gtnewhorizons.angelica.render;

import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;

/**
 * The uniform handles both cloud programs bind.
 */
final class CloudUniforms {
    static final float ALPHA = 0.8f;

    final GlUniformMatrix4f mvp;
    final GlUniformMatrix4f modelView;
    final GlUniformFloat4v colorMult;
    final GlUniformFloat4v fogParams;
    final GlUniformFloat4v fogColor;
    final GlUniformInt fogEnabled;
    final GlUniformInt textureUnit;
    final GlUniformFloat cellHeight;
    final GlUniformFloat4v scroll;
    private final float[] vec4Buf = new float[4];
    private float lastScrollX = Float.NaN, lastScrollZ = Float.NaN;
    private float lastTextureScaleX = Float.NaN, lastTextureScaleZ = Float.NaN;
    private int lastFogEnabled = -1;
    private float lastFogR = Float.NaN, lastFogG = Float.NaN, lastFogB = Float.NaN;
    private float lastColorR = Float.NaN, lastColorG = Float.NaN, lastColorB = Float.NaN;
    private float lastFogParamX = Float.NaN, lastFogParamY = Float.NaN, lastFogParamZ = Float.NaN, lastFogParamW = Float.NaN;
    private float lastCellHeight = Float.NaN;

    CloudUniforms(ShaderBindingContext context) {
        mvp = context.bindUniform("u_MVPMatrix", GlUniformMatrix4f::new);
        modelView = context.bindUniform("u_MVMatrix", GlUniformMatrix4f::new);
        colorMult = context.bindUniform("u_ColorMult", GlUniformFloat4v::new);
        fogParams = context.bindUniform("u_FogParams", GlUniformFloat4v::new);
        fogColor = context.bindUniform("u_FogColor", GlUniformFloat4v::new);
        fogEnabled = context.bindUniform("u_FogEnabled", GlUniformInt::new);
        textureUnit = context.bindUniformIfPresent("u_Tex", GlUniformInt::new);
        cellHeight = context.bindUniformIfPresent("u_CellHeight", GlUniformFloat::new);
        scroll = context.bindUniformIfPresent("u_Scroll", GlUniformFloat4v::new);
    }

    void setScroll(float x, float z, float textureScaleX, float textureScaleZ) {
        if (scroll == null || (x == lastScrollX && z == lastScrollZ && textureScaleX == lastTextureScaleX && textureScaleZ == lastTextureScaleZ)) return;
        lastScrollX = x;
        lastScrollZ = z;
        lastTextureScaleX = textureScaleX;
        lastTextureScaleZ = textureScaleZ;
        vec4Buf[0] = x;
        vec4Buf[1] = z;
        vec4Buf[2] = textureScaleX;
        vec4Buf[3] = textureScaleZ;
        scroll.set(vec4Buf);
    }

    void setCellHeight(float blocks) {
        if (blocks == lastCellHeight) return;
        lastCellHeight = blocks;
        cellHeight.setFloat(blocks);
    }

    void setFogEnabled(boolean enabled) {
        final int enabledValue = enabled ? 1 : 0;
        if (enabledValue != lastFogEnabled) {
            lastFogEnabled = enabledValue;
            fogEnabled.setInt(enabledValue);
        }
    }

    void setFogColor(float r, float g, float b) {
        if (r != lastFogR || g != lastFogG || b != lastFogB) {
            lastFogR = r;
            lastFogG = g;
            lastFogB = b;
            vec4Buf[0] = r;
            vec4Buf[1] = g;
            vec4Buf[2] = b;
            vec4Buf[3] = 1.0f;
            fogColor.set(vec4Buf);
        }
    }

    void setFogParams(float x, float y, float z, float w) {
        if (x != lastFogParamX || y != lastFogParamY || z != lastFogParamZ || w != lastFogParamW) {
            lastFogParamX = x;
            lastFogParamY = y;
            lastFogParamZ = z;
            lastFogParamW = w;
            vec4Buf[0] = x;
            vec4Buf[1] = y;
            vec4Buf[2] = z;
            vec4Buf[3] = w;
            fogParams.set(vec4Buf);
        }
    }

    void setColorMult(float r, float g, float b) {
        if (r != lastColorR || g != lastColorG || b != lastColorB) {
            lastColorR = r;
            lastColorG = g;
            lastColorB = b;
            vec4Buf[0] = r;
            vec4Buf[1] = g;
            vec4Buf[2] = b;
            vec4Buf[3] = ALPHA;
            colorMult.set(vec4Buf);
        }
    }
}
