package com.gtnewhorizons.angelica.render;

import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;

final class CloudUniforms {
    static final float ALPHA = 0.8f;

    final GlUniformMatrix4f mvp;
    final GlUniformMatrix4f mv;
    final GlUniformFloat4v colorMult;
    final GlUniformFloat4v fogParams;
    final GlUniformFloat4v fogColor;
    final GlUniformInt fogEnabled;
    final GlUniformInt tex;

    private int lastFogEnabled = -1;
    private float lastFogR = Float.NaN, lastFogG = Float.NaN, lastFogB = Float.NaN;
    private float lastColorR = Float.NaN, lastColorG = Float.NaN, lastColorB = Float.NaN;
    private float lastFogParamX = Float.NaN, lastFogParamY = Float.NaN, lastFogParamZ = Float.NaN, lastFogParamW = Float.NaN;
    private final float[] uploadBuf = new float[4];

    CloudUniforms(ShaderBindingContext ctx) {
        mvp = ctx.bindUniform("u_MVPMatrix", GlUniformMatrix4f::new);
        mv = ctx.bindUniform("u_MVMatrix", GlUniformMatrix4f::new);
        colorMult = ctx.bindUniform("u_ColorMult", GlUniformFloat4v::new);
        fogParams = ctx.bindUniform("u_FogParams", GlUniformFloat4v::new);
        fogColor = ctx.bindUniform("u_FogColor", GlUniformFloat4v::new);
        fogEnabled = ctx.bindUniform("u_FogEnabled", GlUniformInt::new);
        tex = ctx.bindUniform("u_Tex", GlUniformInt::new);
    }

    void setFogEnabled(boolean enabled) {
        final int val = enabled ? 1 : 0;
        if (val != lastFogEnabled) {
            lastFogEnabled = val;
            fogEnabled.setInt(val);
        }
    }

    void setFogColor(float r, float g, float b) {
        if (r != lastFogR || g != lastFogG || b != lastFogB) {
            lastFogR = r;
            lastFogG = g;
            lastFogB = b;
            uploadBuf[0] = r;
            uploadBuf[1] = g;
            uploadBuf[2] = b;
            uploadBuf[3] = 1.0f;
            fogColor.set(uploadBuf);
        }
    }

    void setFogParams(float x, float y, float z, float w) {
        if (x != lastFogParamX || y != lastFogParamY || z != lastFogParamZ || w != lastFogParamW) {
            lastFogParamX = x;
            lastFogParamY = y;
            lastFogParamZ = z;
            lastFogParamW = w;
            uploadBuf[0] = x;
            uploadBuf[1] = y;
            uploadBuf[2] = z;
            uploadBuf[3] = w;
            fogParams.set(uploadBuf);
        }
    }

    void setColorMult(float r, float g, float b) {
        if (r != lastColorR || g != lastColorG || b != lastColorB) {
            lastColorR = r;
            lastColorG = g;
            lastColorB = b;
            uploadBuf[0] = r;
            uploadBuf[1] = g;
            uploadBuf[2] = b;
            uploadBuf[3] = ALPHA;
            colorMult.set(uploadBuf);
        }
    }
}
