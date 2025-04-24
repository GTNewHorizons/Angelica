package net.coderbot.iris.gl.program;

import net.coderbot.iris.gl.GlResource;
import org.lwjgl.opengl.GL20;

public final class Program extends GlResource {
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;

	Program(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
		super(program);

		this.uniforms = uniforms;
		this.samplers = samplers;
		this.images = images;
	}

	public void use() {
		GL20.glUseProgram(getGlId());

		uniforms.update();
		samplers.update();
		images.update();
	}

	public static void unbind() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GL20.glUseProgram(0);
	}

	@Override
    public void destroyInternal() {
		GL20.glDeleteProgram(getGlId());
	}

	/**
	 * @return the OpenGL ID of this program.
	 * @deprecated this should be encapsulated eventually
	 */
	@Deprecated
	public int getProgramId() {
		return getGlId();
	}

	public int getActiveImages() {
		return images.getActiveImages();
	}
}
