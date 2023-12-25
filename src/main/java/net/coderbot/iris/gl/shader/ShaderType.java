// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.coderbot.iris.gl.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(GL20.GL_VERTEX_SHADER),
	GEOMETRY(GL32.GL_GEOMETRY_SHADER),
    FRAGMENT(GL20.GL_FRAGMENT_SHADER),
    COMPUTE(GL43.GL_COMPUTE_SHADER);

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }
}
