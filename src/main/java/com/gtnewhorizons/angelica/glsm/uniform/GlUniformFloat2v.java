package com.gtnewhorizons.angelica.glsm.uniform;

import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;
import org.lwjgl.opengl.GL20;

public class GlUniformFloat2v extends GlUniform<float[]> {

    public GlUniformFloat2v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 2) {
            throw new IllegalArgumentException("value.length != 2");
        }
        GL20.glUniform2f(this.index, value[0], value[1]);
    }

    public void set(float x, float y) {
        GL20.glUniform2f(this.index, x, y);
    }
}
