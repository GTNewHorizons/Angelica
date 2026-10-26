package com.gtnewhorizons.angelica.client.rendering;

import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;

import java.nio.FloatBuffer;

public class GlUniformFloat3Array extends GlUniform<FloatBuffer> {

    public GlUniformFloat3Array(int index) {
        super(index);
    }

    @Override
    public void set(FloatBuffer value) {
        GLSMPrograms.glUniform3(this.index, value);
    }
}
