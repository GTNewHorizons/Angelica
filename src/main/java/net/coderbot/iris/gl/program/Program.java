package net.coderbot.iris.gl.program;

import lombok.Getter;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

public final class Program extends GlResource {
	
	@Getter
	private final ProgramUniforms uniforms;
	
	@Getter
	private final ProgramSamplers samplers;
	
	@Getter
	private final ProgramImages images;

	Program(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
		super(program);

		this.uniforms = uniforms;
		this.samplers = samplers;
		this.images = images;

		DepthColorStorage.registerOwnedProgram(program);
	}
	
	public void use() {
		GLStateManager.glUseProgram(getGlId());
		
		uniforms.update();
		samplers.update();
		images.update();
	}
	
	public static void unbind() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GLStateManager.glUseProgram(0);
	}

	@Override
    public void destroyInternal() {
		DepthColorStorage.unregisterOwnedProgram(getGlId());
		GLStateManager.glDeleteProgram(getGlId());
	}
	
	public int getProgramId() {
		return getGlId();
	}

	public int getActiveImages() {
		return images.getActiveImages();
	}
}
