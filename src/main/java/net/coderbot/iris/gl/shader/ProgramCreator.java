// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.coderbot.iris.gl.shader;

import net.coderbot.iris.gl.GLDebug;
import net.coderbot.iris.gl.IrisRenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

public class ProgramCreator {
	private static final Logger LOGGER = LogManager.getLogger(ProgramCreator.class);

	public static int create(String name, GlShader... shaders) {
		int program = GL20.glCreateProgram();

		// TODO: This is *really* hardcoded, we need to refactor this to support external calls to glBindAttribLocation
		IrisRenderSystem.bindAttributeLocation(program, 11, "mc_Entity");
		IrisRenderSystem.bindAttributeLocation(program, 12, "mc_midTexCoord");
		IrisRenderSystem.bindAttributeLocation(program, 13, "at_tangent");
		IrisRenderSystem.bindAttributeLocation(program, 14, "at_midBlock");

		for (GlShader shader : shaders) {
            GL20.glAttachShader(program, shader.getHandle());
		}

        GL20.glLinkProgram(program);

		GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);

		//Always detach shaders according to https://www.khronos.org/opengl/wiki/Shader_Compilation#Cleanup
        for (GlShader shader : shaders) {
            IrisRenderSystem.detachShader(program, shader.getHandle());
        }

		String log = IrisRenderSystem.getProgramInfoLog(program);

		if (!log.isEmpty()) {
			LOGGER.warn("Program link log for " + name + ": " + log);
		}

		int result = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);

		if (result != GL11.GL_TRUE) {
			throw new RuntimeException("Shader program linking failed, see log for details");
		}

		return program;
	}
}
