package net.coderbot.iris.sodium.shader_overrides;

import static org.lwjgl.system.MemoryStack.stackPush;

import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import java.nio.FloatBuffer;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderFogComponent;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.SodiumTerrainPipeline;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

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

    private CustomUniforms customUniforms;

	public IrisChunkProgram(RenderDevice owner, ResourceLocation name, int handle, boolean isShadowPass, SodiumTerrainPipeline pipeline, CustomUniforms customUniforms) {
		super(owner, name, handle, ChunkShaderFogComponent.None::new);
		this.uModelViewMatrix = this.getUniformLocation("iris_ModelViewMatrix");
		this.uNormalMatrix = this.getUniformLocation("iris_NormalMatrix");
        this.customUniforms = customUniforms;

        ProgramUniforms.Builder builder = pipeline.initUniforms(handle);
        customUniforms.mapholderToPass(builder, this);
        this.irisProgramUniforms = builder.buildUniforms();
        this.irisProgramSamplers
            = isShadowPass? pipeline.initShadowSamplers(handle) : pipeline.initTerrainSamplers(handle);
        this.irisProgramImages = isShadowPass ? pipeline.initShadowImages(handle) : pipeline.initTerrainImages(handle);
	}

	@Override
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

        customUniforms.push(this);
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

        try (MemoryStack stack = stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);

            matrix.get(buffer);

            RenderSystem.uniformMatrix4fv(location, false, buffer);
        }
	}
}
