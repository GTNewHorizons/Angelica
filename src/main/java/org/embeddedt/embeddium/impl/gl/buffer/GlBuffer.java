package org.embeddedt.embeddium.impl.gl.buffer;

import org.embeddedt.embeddium.impl.gl.GlObject;
import org.lwjgl.opengl.GL20C;

public abstract class GlBuffer extends GlObject {
    private GlBufferMapping activeMapping;

    protected GlBuffer() {
        this.setHandle(GL20C.glGenBuffers());
    }

    public GlBufferMapping getActiveMapping() {
        return this.activeMapping;
    }

    public void setActiveMapping(GlBufferMapping mapping) {
        this.activeMapping = mapping;
    }
}
