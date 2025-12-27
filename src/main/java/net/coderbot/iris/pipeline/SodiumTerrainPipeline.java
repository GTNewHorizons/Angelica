package net.coderbot.iris.pipeline;

import lombok.Getter;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.ExternallyManagedUniforms;
import net.coderbot.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.coderbot.iris.uniforms.custom.CustomUniforms;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

import net.coderbot.iris.Iris;

public class SodiumTerrainPipeline {
	Optional<String> terrainVertex = Optional.empty();
	Optional<String> terrainGeometry = Optional.empty();
	Optional<String> terrainFragment = Optional.empty();
	Optional<String> translucentVertex = Optional.empty();
	Optional<String> translucentGeometry = Optional.empty();
	Optional<String> translucentFragment = Optional.empty();
	Optional<String> shadowVertex = Optional.empty();
	Optional<String> shadowGeometry = Optional.empty();
	Optional<String> shadowFragment = Optional.empty();
	//GlFramebuffer framebuffer;
	ProgramSet programSet;

    @Getter
    private final CustomUniforms customUniforms;

	private final WorldRenderingPipeline parent;

	private final IntFunction<ProgramSamplers> createTerrainSamplers;
	private final IntFunction<ProgramSamplers> createShadowSamplers;

	private final IntFunction<ProgramImages> createTerrainImages;
	private final IntFunction<ProgramImages> createShadowImages;

	public SodiumTerrainPipeline(WorldRenderingPipeline parent,
								 ProgramSet programSet, IntFunction<ProgramSamplers> createTerrainSamplers,
								 IntFunction<ProgramSamplers> createShadowSamplers,
								 IntFunction<ProgramImages> createTerrainImages,
								 IntFunction<ProgramImages> createShadowImages, CustomUniforms customUniforms,
								 String terrainSourceName,
								 String translucentSourceName,
								 String shadowSourceName,
								 CompletableFuture<Map<PatchShaderType, String>> terrainFuture,
								 CompletableFuture<Map<PatchShaderType, String>> translucentFuture,
								 CompletableFuture<Map<PatchShaderType, String>> shadowFuture) {
		this.parent = Objects.requireNonNull(parent);

		this.programSet = programSet;

        this.customUniforms = customUniforms;

		if (terrainFuture != null) {
			try {
				Map<PatchShaderType, String> result = terrainFuture.join();
				terrainVertex = Optional.ofNullable(result.get(PatchShaderType.VERTEX));
				terrainGeometry = Optional.ofNullable(result.get(PatchShaderType.GEOMETRY));
				terrainFragment = Optional.ofNullable(result.get(PatchShaderType.FRAGMENT));

				PatchedShaderPrinter.debugPatchedShaders(terrainSourceName + "_sodium",
					terrainVertex.orElse(null), terrainGeometry.orElse(null), terrainFragment.orElse(null));
			} catch (Exception e) {
				Iris.logger.error("Failed to transform terrain shader: {}", terrainSourceName, e);
				throw new RuntimeException("Shader transformation failed for " + terrainSourceName, e);
			}
		}

		if (translucentFuture != null) {
			try {
				Map<PatchShaderType, String> result = translucentFuture.join();
				translucentVertex = Optional.ofNullable(result.get(PatchShaderType.VERTEX));
				translucentGeometry = Optional.ofNullable(result.get(PatchShaderType.GEOMETRY));
				translucentFragment = Optional.ofNullable(result.get(PatchShaderType.FRAGMENT));

				PatchedShaderPrinter.debugPatchedShaders(translucentSourceName + "_sodium",
					translucentVertex.orElse(null), translucentGeometry.orElse(null), translucentFragment.orElse(null));
			} catch (Exception e) {
				Iris.logger.error("Failed to transform translucent shader: {}", translucentSourceName, e);
				throw new RuntimeException("Shader transformation failed for " + translucentSourceName, e);
			}
		}

		if (shadowFuture != null) {
			try {
				Map<PatchShaderType, String> result = shadowFuture.join();
				shadowVertex = Optional.ofNullable(result.get(PatchShaderType.VERTEX));
				shadowGeometry = Optional.ofNullable(result.get(PatchShaderType.GEOMETRY));
				shadowFragment = Optional.ofNullable(result.get(PatchShaderType.FRAGMENT));

				PatchedShaderPrinter.debugPatchedShaders(shadowSourceName + "_sodium",
					shadowVertex.orElse(null), shadowGeometry.orElse(null), shadowFragment.orElse(null));
			} catch (Exception e) {
				Iris.logger.error("Failed to transform shadow shader: {}", shadowSourceName, e);
				throw new RuntimeException("Shader transformation failed for " + shadowSourceName, e);
			}
		}

		this.createTerrainSamplers = createTerrainSamplers;
		this.createShadowSamplers = createShadowSamplers;
		this.createTerrainImages = createTerrainImages;
		this.createShadowImages = createShadowImages;
	}

	public Optional<String> getTerrainVertexShaderSource() {
		return terrainVertex;
	}

	public Optional<String> getTerrainGeometryShaderSource() {
		return terrainGeometry;
	}

	public Optional<String> getTerrainFragmentShaderSource() {
		return terrainFragment;
	}

	public Optional<String> getTranslucentVertexShaderSource() {
		return translucentVertex;
	}

	public Optional<String> getTranslucentGeometryShaderSource() {
		return translucentGeometry;
	}

	public Optional<String> getTranslucentFragmentShaderSource() {
		return translucentFragment;
	}

	public Optional<String> getShadowVertexShaderSource() {
		return shadowVertex;
	}

	public Optional<String> getShadowGeometryShaderSource() {
		return shadowGeometry;
	}

	public Optional<String> getShadowFragmentShaderSource() {
		return shadowFragment;
	}

	public ProgramUniforms.Builder initUniforms(int programId) {
		ProgramUniforms.Builder uniforms = ProgramUniforms.builder("<sodium shaders>", programId);

        CommonUniforms.addDynamicUniforms(uniforms);
        customUniforms.assignTo(uniforms);

        ExternallyManagedUniforms.addExternallyManagedUniforms116(uniforms);

        BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
        return uniforms;
	}

	public boolean hasShadowPass() {
		return createShadowSamplers != null;
	}

	public ProgramSamplers initTerrainSamplers(int programId) {
		return createTerrainSamplers.apply(programId);
	}

	public ProgramSamplers initShadowSamplers(int programId) {
		return createShadowSamplers.apply(programId);
	}

	public ProgramImages initTerrainImages(int programId) {
		return createTerrainImages.apply(programId);
	}

	public ProgramImages initShadowImages(int programId) {
		return createShadowImages.apply(programId);
	}

	/*public void bindFramebuffer() {
		this.framebuffer.bind();
	}

	public void unbindFramebuffer() {
		GlStateManager.bindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
	}*/
}
