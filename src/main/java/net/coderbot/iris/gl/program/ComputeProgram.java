package net.coderbot.iris.gl.program;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.FilledIndirectPointer;
import org.joml.Vector2f;
import org.joml.Vector3i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.nio.IntBuffer;

public final class ComputeProgram extends GlResource {
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;
	private Vector3i absoluteWorkGroups;
	private Vector2f relativeWorkGroups;
	private FilledIndirectPointer indirectPointer;
	private final IntBuffer localSizeBuffer;
	private float cachedWidth;
	private float cachedHeight;
	private Vector3i cachedWorkGroups;

	ComputeProgram(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
		super(program);

        localSizeBuffer = BufferUtils.createIntBuffer(3);
		RenderSystem.getProgramiv(program, GL43.GL_COMPUTE_WORK_GROUP_SIZE, localSizeBuffer);
		this.uniforms = uniforms;
		this.samplers = samplers;
		this.images = images;
	}

	public void setWorkGroupInfo(Vector2f relativeWorkGroups, Vector3i absoluteWorkGroups, FilledIndirectPointer indirectPointer) {
		this.relativeWorkGroups = relativeWorkGroups;
		this.absoluteWorkGroups = absoluteWorkGroups;
		this.indirectPointer = indirectPointer;
	}

	public Vector3i getWorkGroups(float width, float height) {
		if (indirectPointer != null) return null;

		if (cachedWidth != width || cachedHeight != height || cachedWorkGroups == null) {
			this.cachedWidth = width;
			this.cachedHeight = height;
			if (this.absoluteWorkGroups != null) {
				this.cachedWorkGroups = this.absoluteWorkGroups;
			} else if (relativeWorkGroups != null) {
				// Do not use actual localSize here, apparently that's not what we want.
				this.cachedWorkGroups = new Vector3i((int) Math.ceil(Math.ceil((width * relativeWorkGroups.x)) / localSizeBuffer.get(0)), (int) Math.ceil(Math.ceil((height * relativeWorkGroups.y)) / localSizeBuffer.get(1)), 1);
			} else {
				this.cachedWorkGroups = new Vector3i((int) Math.ceil(width / localSizeBuffer.get(0)), (int) Math.ceil(height / localSizeBuffer.get(1)), 1);
			}
		}

		return cachedWorkGroups;
	}

    public void use() {
        GL20.glUseProgram(getGlId());

        uniforms.update();
        samplers.update();
        images.update();
    }

	public void dispatch(float width, float height) {
		if (!Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::allowConcurrentCompute).orElse(false)) {
			RenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
		}

		if (indirectPointer != null) {
			RenderSystem.bindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, indirectPointer.buffer());
			RenderSystem.dispatchComputeIndirect(indirectPointer.offset());
		} else {
			RenderSystem.dispatchCompute(getWorkGroups(width, height));
		}
	}

	public static void unbind() {
		ProgramUniforms.clearActiveUniforms();
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
