// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.coderbot.iris.gl.shader;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

public class ProgramCreator {

	private static final Logger LOGGER = LogManager.getLogger(ProgramCreator.class);

	public static final int MC_ENTITY = 11;
	public static final int MC_MID_TEX_COORD = 12;
	public static final int AT_TANGENT = 13;
	public static final int AT_MIDBLOCK = 14;

	public static int create(String name, GlShader... shaders) {
		int program = GL20.glCreateProgram();

		// TODO: This is *really* hardcoded, we need to refactor this to support external calls to glBindAttribLocation
		RenderSystem.bindAttributeLocation(program, MC_ENTITY, "mc_Entity");
		RenderSystem.bindAttributeLocation(program, MC_MID_TEX_COORD, "mc_midTexCoord");
		RenderSystem.bindAttributeLocation(program, AT_TANGENT, "at_tangent");
		RenderSystem.bindAttributeLocation(program, AT_MIDBLOCK, "at_midBlock");

		for (GlShader shader : shaders) {
            GL20.glAttachShader(program, shader.getHandle());
		}

        GL20.glLinkProgram(program);

		GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);

		//Always detach shaders according to https://www.khronos.org/opengl/wiki/Shader_Compilation#Cleanup
        for (GlShader shader : shaders) {
            RenderSystem.detachShader(program, shader.getHandle());
        }

		String log = RenderSystem.getProgramInfoLog(program);

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
