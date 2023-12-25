package net.coderbot.iris.sodium.shader_overrides;

import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderFogComponent;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

public class IrisChunkProgram extends ChunkProgram {
	// Uniform variable binding indexes
	private final int uModelViewMatrix;
	private final int uNormalMatrix;

	@Nullable
	private final ProgramUniforms irisProgramUniforms;

	@Nullable
	private final ProgramSamplers irisProgramSamplers;

	@Nullable
	private final ProgramImages irisProgramImages;

	public IrisChunkProgram(RenderDevice owner, ResourceLocation name, int handle,
							@Nullable ProgramUniforms irisProgramUniforms, @Nullable ProgramSamplers irisProgramSamplers,
							@Nullable ProgramImages irisProgramImages) {
		super(owner, name, handle, ChunkShaderFogComponent.None::new);
		this.uModelViewMatrix = this.getUniformLocation("iris_ModelViewMatrix");
		this.uNormalMatrix = this.getUniformLocation("iris_NormalMatrix");
		this.irisProgramUniforms = irisProgramUniforms;
		this.irisProgramSamplers = irisProgramSamplers;
		this.irisProgramImages = irisProgramImages;
	}

	public void setup(MatrixStack matrixStack, float modelScale, float textureScale) {
		super.setup(matrixStack, modelScale, textureScale);

		if (irisProgramUniforms != null) {
			irisProgramUniforms.update();
		}

		if (irisProgramSamplers != null) {
			irisProgramSamplers.update();
		}

		if (irisProgramImages != null) {
			irisProgramImages.update();
		}

		Matrix4f modelViewMatrix = matrixStack.peek().getModel();
		Matrix4f normalMatrix = new Matrix4f(matrixStack.peek().getModel());
		normalMatrix.invert();
		normalMatrix.transpose();

		uniformMatrix(uModelViewMatrix, modelViewMatrix);
		uniformMatrix(uNormalMatrix, normalMatrix);
	}

	@Override
	public int getUniformLocation(String name) {
		// NB: We pass through calls involving u_ModelViewProjectionMatrix, u_ModelScale, and u_TextureScale, since
		//     currently patched Iris shader programs use those.

		if ("iris_BlockTex".equals(name) || "iris_LightTex".equals(name)) {
			// Not relevant for Iris shader programs
			return -1;
		}

		try {
			return super.getUniformLocation(name);
		} catch (NullPointerException e) {
			// Suppress getUniformLocation
			return -1;
		}
	}

	private void uniformMatrix(int location, Matrix4f matrix) {
		if (location == -1) {
			return;
		}

        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

        matrix.get(buffer);

        IrisRenderSystem.uniformMatrix4fv(location, false, buffer);
	}
}
