package net.coderbot.iris.gl.program;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;
import com.gtnewhorizons.angelica.glsm.shader.ProgramBinaryCache;
import net.coderbot.iris.gl.image.ImageHolder;
import net.coderbot.iris.gl.sampler.GlSampler;
import net.coderbot.iris.gl.sampler.SamplerHolder;
import net.coderbot.iris.gl.shader.GlShader;
import net.coderbot.iris.gl.shader.ProgramCreator;
import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import com.gtnewhorizons.angelica.glsm.texture.TextureType;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

public class ProgramBuilder extends ProgramUniforms.Builder implements SamplerHolder, ImageHolder {
	private final int program;
	private final ProgramSamplers.Builder samplers;
	private final ProgramImages.Builder images;

	private ProgramBuilder(String name, int program, ImmutableSet<Integer> reservedTextureUnits) {
		super(name, program);

		this.program = program;
		this.samplers = ProgramSamplers.builder(program, reservedTextureUnits);
		this.images = ProgramImages.builder(program);
	}

	public void bindAttributeLocation(int index, String name) {
		RenderSystem.bindAttributeLocation(program, index, name);
	}

	public static ProgramBuilder begin(String name, @Nullable String vertexSource, @Nullable String geometrySource,
									   @Nullable String fragmentSource, ImmutableSet<Integer> reservedTextureUnits) {
		return begin(name, vertexSource, geometrySource, null, null, fragmentSource, reservedTextureUnits);
	}

	public static ProgramBuilder begin(String name, @Nullable String vertexSource, @Nullable String geometrySource,
									   @Nullable String tessControlSource, @Nullable String tessEvalSource,
									   @Nullable String fragmentSource, ImmutableSet<Integer> reservedTextureUnits) {
		final ProgramBinaryCache.Key cacheKey = ProgramCreator.cacheKey();
		if (cacheKey != null) {
			cacheKey.stage(ShaderType.VERTEX.id, vertexSource);
			if (geometrySource != null) cacheKey.stage(ShaderType.GEOMETRY.id, geometrySource);
			if (tessControlSource != null) cacheKey.stage(ShaderType.TESSELATION_CONTROL.id, tessControlSource);
			if (tessEvalSource != null) cacheKey.stage(ShaderType.TESSELATION_EVAL.id, tessEvalSource);
			cacheKey.stage(ShaderType.FRAGMENT.id, fragmentSource);
			final int saved = ProgramCreator.load(name, cacheKey);
			if (saved != 0) {
				initializeFallbackUniforms(saved);
				return new ProgramBuilder(name, saved, reservedTextureUnits);
			}
		}

		GlShader vertex = buildShader(ShaderType.VERTEX, name + ".vsh", vertexSource);
		GlShader geometry = geometrySource != null ? buildShader(ShaderType.GEOMETRY, name + ".gsh", geometrySource) : null;
		GlShader tessControl = tessControlSource != null ? buildShader(ShaderType.TESSELATION_CONTROL, name + ".tcs", tessControlSource) : null;
		GlShader tessEval = tessEvalSource != null ? buildShader(ShaderType.TESSELATION_EVAL, name + ".tes", tessEvalSource) : null;
		GlShader fragment = buildShader(ShaderType.FRAGMENT, name + ".fsh", fragmentSource);

		java.util.List<GlShader> shaders = new java.util.ArrayList<>();
		shaders.add(vertex);
		if (geometry != null) shaders.add(geometry);
		if (tessControl != null) shaders.add(tessControl);
		if (tessEval != null) shaders.add(tessEval);
		shaders.add(fragment);

		int programId = ProgramCreator.create(name, cacheKey, shaders.toArray(new GlShader[0]));
		initializeFallbackUniforms(programId);

		for (GlShader shader : shaders) {
			shader.destroy();
		}

		return new ProgramBuilder(name, programId, reservedTextureUnits);
	}

	/** Initialize alpha and color uniforms to neutral values until the first pass update. */
	private static void initializeFallbackUniforms(int programId) {
		final int previous = GLStateManager.getActiveProgram();
		final CommandRecorder recorder = DisplayListManager.isRecording() ? DisplayListManager.pauseRecording() : null;
		try {
			Program.bindManaged(programId);
			seedUniform1i(programId, "iris_currentAlphaFunc", 7);
			seedUniform1f(programId, "iris_currentAlphaTest", -1.0f);
			seedUniform1f(programId, "alphaTestRef", -1.0f);
			final int modulator = GLStateManager.glGetUniformLocation(programId, "iris_ColorModulator");
			if (modulator != -1) {
				RenderSystem.uniform4f(modulator, 1.0f, 1.0f, 1.0f, 1.0f);
			}
		} finally {
			try {
				Program.bindManaged(previous);
			} finally {
				if (recorder != null) DisplayListManager.resumeRecording(recorder);
			}
		}
	}

	private static void seedUniform1i(int programId, String name, int value) {
		final int location = GLStateManager.glGetUniformLocation(programId, name);
		if (location != -1) {
			RenderSystem.uniform1i(location, value);
		}
	}

	private static void seedUniform1f(int programId, String name, float value) {
		final int location = GLStateManager.glGetUniformLocation(programId, name);
		if (location != -1) {
			RenderSystem.uniform1f(location, value);
		}
	}

	public static ProgramBuilder beginCompute(String name, @Nullable String source, ImmutableSet<Integer> reservedTextureUnits) {
		if (!RenderSystem.supportsCompute()) {
			throw new IllegalStateException("This PC does not support compute shaders, but it's attempting to be used???");
		}

		final ProgramBinaryCache.Key cacheKey = ProgramCreator.cacheKey();
		if (cacheKey != null) {
			cacheKey.stage(ShaderType.COMPUTE.id, source);
			final int saved = ProgramCreator.load(name, cacheKey);
			if (saved != 0) {
				return new ProgramBuilder(name, saved, reservedTextureUnits);
			}
		}

		GlShader compute = buildShader(ShaderType.COMPUTE, name + ".csh", source);

		int programId = ProgramCreator.create(name, cacheKey, compute);

		compute.destroy();

		return new ProgramBuilder(name, programId, reservedTextureUnits);
	}

	public Program build() {
		return new Program(program, super.buildUniforms(), this.samplers.build(), this.images.build());
	}

	public ComputeProgram buildCompute() {
		return new ComputeProgram(program, super.buildUniforms(), this.samplers.build(), this.images.build());
	}

	private static GlShader buildShader(ShaderType shaderType, String name, @Nullable String source) {
		try {
			return new GlShader(shaderType, name, source);
		} catch (RuntimeException e) {
			throw new RuntimeException("Failed to compile " + shaderType + " shader for program " + name, e);
		}
	}

	@Override
	public void addExternalSampler(int textureUnit, String... names) {
		samplers.addExternalSampler(textureUnit, names);
	}

	@Override
	public boolean hasSampler(String name) {
		return samplers.hasSampler(name);
	}

	@Override
	public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
		return samplers.addDefaultSampler(type, texture, notifier, sampler, names);
	}

	@Override
	public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
		return samplers.addDynamicSampler(type, texture, sampler, names);
	}

	@Override
	public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
		return samplers.addDynamicSampler(type, texture, notifier, sampler, names);
	}

	@Override
	public boolean hasImage(String name) {
		return images.hasImage(name);
	}

	@Override
	public void addTextureImage(IntSupplier textureID, InternalTextureFormat internalFormat, String name) {
		images.addTextureImage(textureID, internalFormat, name);
	}
}
