package org.embeddedt.embeddium.impl.gl.tessellation;

import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL43C;

public enum GlPrimitiveType {
    TRIANGLES(GL20C.GL_TRIANGLES),
    PATCHES(GL43C.GL_PATCHES);

    private final int id;

    GlPrimitiveType(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}
