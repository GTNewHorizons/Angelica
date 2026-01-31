package net.coderbot.iris.gl.program;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.image.ImageHolder;
import net.coderbot.iris.gl.sampler.GlSampler;
import net.coderbot.iris.gl.sampler.SamplerHolder;
import net.coderbot.iris.gl.shader.GlShader;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.gl.texture.TextureType;
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

		int programId = ProgramCreator.create(name, shaders.toArray(new GlShader[0]));

		for (GlShader shader : shaders) {
			shader.destroy();
		}

		return new ProgramBuilder(name, programId, reservedTextureUnits);
	}

	public static ProgramBuilder beginCompute(String name, @Nullable String source, ImmutableSet<Integer> reservedTextureUnits) {
		if (!RenderSystem.supportsCompute()) {
			throw new IllegalStateException("This PC does not support compute shaders, but it's attempting to be used???");
		}

		GlShader compute = buildShader(ShaderType.COMPUTE, name + ".csh", source);

		int programId = ProgramCreator.create(name, compute);

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
