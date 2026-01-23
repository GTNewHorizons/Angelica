package net.coderbot.iris.gl.sampler;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

public class SamplerLimits {
	private static SamplerLimits instance;
	private final int maxTextureUnits;
	private final int maxDrawBuffers;
	private final int maxShaderStorageUnits;

	private SamplerLimits() {
		this.maxTextureUnits = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS);
		this.maxDrawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
		this.maxShaderStorageUnits = RenderSystem.supportsSSBO() ? GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) : 0;
	}

	public static SamplerLimits get() {
		if (instance == null) {
			instance = new SamplerLimits();
		}

		return instance;
	}

	public int getMaxTextureUnits() {
		return maxTextureUnits;
	}

	public int getMaxDrawBuffers() {
		return maxDrawBuffers;
	}

	public int getMaxShaderStorageUnits() {
		return maxShaderStorageUnits;
	}
}
