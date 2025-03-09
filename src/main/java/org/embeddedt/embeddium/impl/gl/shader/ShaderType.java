package org.embeddedt.embeddium.impl.gl.shader;

import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL42C;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(GL20C.GL_VERTEX_SHADER),
    FRAGMENT(GL20C.GL_FRAGMENT_SHADER),
    GEOM(GL32C.GL_GEOMETRY_SHADER),
    TESS_CTRL(GL42C.GL_TESS_CONTROL_SHADER),
    TESS_EVALUATE(GL42C.GL_TESS_EVALUATION_SHADER);

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }
}
