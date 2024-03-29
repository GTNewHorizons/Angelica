package me.jellysquid.mods.sodium.client.gl.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER),
    GEOMETRY(GL32.GL_GEOMETRY_SHADER);

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }
}
