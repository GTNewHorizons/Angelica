// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.coderbot.iris.gl.shader;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.shader.ProgramBinaryCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

public class ProgramCreator {

	private static final Logger LOGGER = LogManager.getLogger("ProgramCreator");

	public static final int MC_ENTITY = ImmediateExtendedAttribHandler.LOC_MC_ENTITY;
	public static final int MC_MID_TEX_COORD = ImmediateExtendedAttribHandler.LOC_MID_TEX;
	public static final int AT_TANGENT = ImmediateExtendedAttribHandler.LOC_TANGENT;
	public static final int AT_MIDBLOCK = ImmediateExtendedAttribHandler.LOC_MIDBLOCK;

	// TODO: This is *really* hardcoded, we need to refactor this to support external calls to glBindAttribLocation
	private static final String[] ATTRIBUTE_NAMES = { "iris_Entity", "mc_Entity", "mc_midTexCoord", "iris_CubeTangent", "at_tangent", "at_midBlock" };
	private static final int[] ATTRIBUTE_LOCATIONS = { MC_ENTITY, MC_ENTITY, MC_MID_TEX_COORD, AT_TANGENT, AT_TANGENT, AT_MIDBLOCK };

	@Nullable
	public static ProgramBinaryCache.Key cacheKey() {
		if (!ProgramBinaryCache.isEnabled()) return null;
		final ProgramBinaryCache.Key key = ProgramBinaryCache.key();
		for (int i = 0; i < ATTRIBUTE_NAMES.length; i++) {
			key.attribute(ATTRIBUTE_NAMES[i], ATTRIBUTE_LOCATIONS[i]);
		}
		return key;
	}

	public static int load(String name, ProgramBinaryCache.Key cacheKey) {
		final int program = ProgramBinaryCache.load(cacheKey);
		if (program != 0) {
			GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);
		}
		return program;
	}

	public static int create(String name, GlShader... shaders) {
		return create(name, null, shaders);
	}

	public static int create(String name, @Nullable ProgramBinaryCache.Key cacheKey, GlShader... shaders) {
		int program = GLStateManager.glCreateProgram();

		for (int i = 0; i < ATTRIBUTE_NAMES.length; i++) {
			RenderSystem.bindAttributeLocation(program, ATTRIBUTE_LOCATIONS[i], ATTRIBUTE_NAMES[i]);
		}

		for (GlShader shader : shaders) {
            GLStateManager.glAttachShader(program, shader.getHandle());
		}

        GLStateManager.glLinkProgram(program);

		GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);

		//Always detach shaders according to https://www.khronos.org/opengl/wiki/Shader_Compilation#Cleanup
        for (GlShader shader : shaders) {
            RenderSystem.detachShader(program, shader.getHandle());
        }

		String log = RenderSystem.getProgramInfoLog(program);

		if (!log.isEmpty()) {
			LOGGER.warn("Program link log for " + name + ": " + log);
		}

		int result = GLStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS);

		if (result != GL11.GL_TRUE) {
			throw new RuntimeException("Shader program linking failed, see log for details");
		}

		if (cacheKey != null) {
			ProgramBinaryCache.save(cacheKey, program);
		}

		return program;
	}
}
