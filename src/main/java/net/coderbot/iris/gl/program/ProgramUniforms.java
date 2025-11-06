package net.coderbot.iris.gl.program;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Supplier;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.coderbot.iris.gl.uniform.DynamicLocationalUniformHolder;
import net.coderbot.iris.gl.uniform.Uniform;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformType;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.joml.Vector3i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBShaderImageLoadStore;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class ProgramUniforms {
	private static ProgramUniforms active;
	private final ImmutableList<Uniform> perTick;
	private final ImmutableList<Uniform> perFrame;
	private final ImmutableList<Uniform> dynamic;
	private final ImmutableList<ValueUpdateNotifier> notifiersToReset;

	private ImmutableList<Uniform> once;
	long lastTick = -1;
	int lastFrame = -1;

	public ProgramUniforms(ImmutableList<Uniform> once, ImmutableList<Uniform> perTick, ImmutableList<Uniform> perFrame,
						   ImmutableList<Uniform> dynamic, ImmutableList<ValueUpdateNotifier> notifiersToReset) {
		this.once = once;
		this.perTick = perTick;
		this.perFrame = perFrame;
		this.dynamic = dynamic;
		this.notifiersToReset = notifiersToReset;
	}

	private void updateStage(ImmutableList<Uniform> uniforms) {
		for (Uniform uniform : uniforms) {
			uniform.update();
		}
	}

	private static long getCurrentTick() {
        final WorldClient world = Minecraft.getMinecraft().theWorld;
		if (world != null) {
            return AngelicaConfig.useTotalWorldTime
                ? world.getTotalWorldTime()
                : world.getWorldTime();
		} else {
			return 0L;
		}
	}

	public void update() {
		if (active != null) {
			active.removeListeners();
		}

		active = this;

		updateStage(dynamic);

		if (once != null) {
			updateStage(once);
			updateStage(perTick);
			updateStage(perFrame);
			lastTick = getCurrentTick();

			once = null;
			return;
		}

		long currentTick = getCurrentTick();

		if (lastTick != currentTick) {
			lastTick = currentTick;

			updateStage(perTick);
		}

		// TODO: Move the frame counter to a different place?
		int currentFrame = SystemTimeUniforms.COUNTER.getAsInt();

		if (lastFrame != currentFrame) {
			lastFrame = currentFrame;

			updateStage(perFrame);
		}
	}

	public void removeListeners() {
		active = null;

		for (ValueUpdateNotifier notifier : notifiersToReset) {
			notifier.setListener(null);
		}
	}

	public static void clearActiveUniforms() {
		if (active != null) {
			active.removeListeners();
		}
	}

	public static Builder builder(String name, int program) {
		return new Builder(name, program);
	}

	public static class Builder implements DynamicLocationalUniformHolder {
		private final String name;
		private final int program;

		private final Map<Integer, String> locations;
		private final Map<String, Uniform> once;
		private final Map<String, Uniform> perTick;
		private final Map<String, Uniform> perFrame;
		private final Map<String, Uniform> dynamic;
		private final Map<String, UniformType> uniformNames;
		private final Map<String, UniformType> externalUniformNames;
		private final List<ValueUpdateNotifier> notifiersToReset;

		protected Builder(String name, int program) {
			this.name = name;
			this.program = program;

			locations = new HashMap<>();
			once = new HashMap<>();
			perTick = new HashMap<>();
			perFrame = new HashMap<>();
			dynamic = new HashMap<>();
			uniformNames = new HashMap<>();
			externalUniformNames = new HashMap<>();
			notifiersToReset = new ArrayList<>();
		}

		@Override
		public Builder addUniform(UniformUpdateFrequency updateFrequency, Uniform uniform) {
			Objects.requireNonNull(uniform);

			switch (updateFrequency) {
				case ONCE:
					once.put(locations.get(uniform.getLocation()), uniform);
					break;
				case PER_TICK:
					perTick.put(locations.get(uniform.getLocation()), uniform);
					break;
				case PER_FRAME:
					perFrame.put(locations.get(uniform.getLocation()), uniform);
					break;
			}

			return this;
		}

		@Override
		public OptionalInt location(String name, UniformType type) {
			int id = RenderSystem.getUniformLocation(program, name);

			if (id == -1) {
				return OptionalInt.empty();
			}

			if ((!locations.containsKey(id) && !uniformNames.containsKey(name))) {
				locations.put(id, name);
				uniformNames.put(name, type);
			} else {
				Iris.logger.warn("[" + this.name + "] Duplicate uniform: " + type.toString().toLowerCase() + " " + name);

				return OptionalInt.empty();
			}

			return OptionalInt.of(id);
		}

		public ProgramUniforms buildUniforms() {
			// Check for any unsupported uniforms and warn about them so that we can easily figure out what uniforms we need to add.
			final int activeUniforms = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
			IntBuffer sizeType = BufferUtils.createIntBuffer(2);

			for (int index = 0; index < activeUniforms; index++) {
				final String name = RenderSystem.getActiveUniform(program, index, 128, sizeType);

				if (name.isEmpty()) {
					// No further information available.
					continue;
				}

				final int size = sizeType.get(0);
				final int type = sizeType.get(1);

				final UniformType provided = uniformNames.get(name);
                final UniformType expected = getExpectedType(type);

				if(AngelicaConfig.enableHardcodedCustomUniforms) {
					// Legacy Checks from hardcoded custom uniforms
					if (provided == null && !name.startsWith("gl_")) {
						final String typeName = getTypeName(type);

						if (isSampler(type) || isImage(type)) {
							// don't print a warning, samplers and images are managed elsewhere.
							continue;
						}

						final UniformType externalProvided = externalUniformNames.get(name);

						if (externalProvided != null) {
							if (externalProvided != expected) {
								final String expectedName;

								if (expected != null) {
									expectedName = expected.toString();
								} else {
									expectedName = "(unsupported type: " + getTypeName(type) + ")";
								}

								Iris.logger.error("[" + this.name + "] Wrong uniform type for externally-managed uniform " + name + ": " + externalProvided + " is provided but the program expects " + expectedName + ".");
							}

							continue;
						}

						if (size == 1) {
							Iris.logger.warn("[" + this.name + "] Unsupported uniform: " + typeName + " " + name);
						} else {
							Iris.logger.warn("[" + this.name + "] Unsupported uniform: " + name + " of size " + size + " and type " + typeName);
						}

						continue;
					}
				}

				if (provided != null && provided != expected) {
					String expectedName;

					if (expected != null) {
						expectedName = expected.toString();
					} else {
						expectedName = "(unsupported type: " + getTypeName(type) + ")";
					}

					Iris.logger.error("[" + this.name + "] Wrong uniform type for " + name + ": Iris is providing " + provided + " but the program expects " + expectedName + ". Disabling that uniform.");

					once.remove(name);
					perTick.remove(name);
					perFrame.remove(name);
					dynamic.remove(name);
				}
			}

			return new ProgramUniforms(ImmutableList.copyOf(once.values()), ImmutableList.copyOf(perTick.values()), ImmutableList.copyOf(perFrame.values()),
					ImmutableList.copyOf(dynamic.values()), ImmutableList.copyOf(notifiersToReset));
		}

		@Override
		public Builder addDynamicUniform(Uniform uniform, ValueUpdateNotifier notifier) {
			Objects.requireNonNull(uniform);
            if(notifier == null){
                Iris.logger.info("notifier is null: " + uniform.getLocation());
            }

			Objects.requireNonNull(notifier);

			dynamic.put(locations.get(uniform.getLocation()), uniform);
			notifiersToReset.add(notifier);

			return this;
		}

        @Override
		public UniformHolder externallyManagedUniform(String name, UniformType type) {
			externalUniformNames.put(name, type);

			return this;
		}
	}

	private static String getTypeName(int type) {
        return switch(type) {
            case GL11.GL_FLOAT -> "float";
            case GL11.GL_INT -> "int";
            case GL20.GL_BOOL -> "bool";
            case GL20.GL_FLOAT_MAT4 -> "mat4";
            case GL20.GL_FLOAT_VEC4 -> "vec4";
            case GL20.GL_FLOAT_MAT3 -> "mat3";
            case GL20.GL_FLOAT_VEC3 -> "vec3";
            case GL20.GL_FLOAT_MAT2 -> "mat2";
            case GL20.GL_FLOAT_VEC2 -> "vec2";
            case GL20.GL_INT_VEC2 -> "ivec2";
            case GL20.GL_INT_VEC4 -> "ivec4";
            case GL20.GL_SAMPLER_3D -> "sampler3D";
            case GL20.GL_SAMPLER_2D -> "sampler2D";
            case GL30.GL_UNSIGNED_INT_SAMPLER_2D -> "usampler2D";
            case GL30.GL_UNSIGNED_INT_SAMPLER_3D -> "usampler3D";
            case GL20.GL_SAMPLER_1D -> "sampler1D";
            case GL20.GL_SAMPLER_2D_SHADOW -> "sampler2DShadow";
            case GL20.GL_SAMPLER_1D_SHADOW -> "sampler1DShadow";
            case ARBShaderImageLoadStore.GL_IMAGE_2D -> "image2D";
            case ARBShaderImageLoadStore.GL_IMAGE_3D -> "image3D";
            default -> "(unknown:" + type + ")";
        };
	}

	private static UniformType getExpectedType(int type) {
        return switch (type) {
            case GL11.GL_FLOAT -> UniformType.FLOAT;
            case GL11.GL_INT -> UniformType.INT;
            case GL20.GL_BOOL -> UniformType.INT;
            case GL20.GL_FLOAT_MAT4 -> UniformType.MAT4;
            case GL20.GL_FLOAT_VEC4 -> UniformType.VEC4;
            case GL20.GL_INT_VEC4 -> UniformType.VEC4I;
            case GL20.GL_FLOAT_VEC3 -> UniformType.VEC3;
            case GL20.GL_FLOAT_MAT3 -> UniformType.MAT3;
            case GL20.GL_INT_VEC3 -> UniformType.VEC3I;
            case GL20.GL_FLOAT_MAT2 -> null;
            case GL20.GL_FLOAT_VEC2 -> UniformType.VEC2;
            case GL20.GL_INT_VEC2 -> UniformType.VEC2I;
            case GL20.GL_SAMPLER_3D -> UniformType.INT;
            case GL20.GL_SAMPLER_2D -> UniformType.INT;
            case GL30.GL_UNSIGNED_INT_SAMPLER_2D -> UniformType.INT;
            case GL30.GL_UNSIGNED_INT_SAMPLER_3D -> UniformType.INT;
            case GL20.GL_SAMPLER_1D -> UniformType.INT;
            case GL20.GL_SAMPLER_2D_SHADOW -> UniformType.INT;
            case GL20.GL_SAMPLER_1D_SHADOW -> UniformType.INT;
            default -> null;
        };
	}

	private static boolean isSampler(int type) {
		return type == GL20.GL_SAMPLER_1D
				|| type == GL20.GL_SAMPLER_2D
				|| type == GL30.GL_UNSIGNED_INT_SAMPLER_2D
				|| type == GL30.GL_UNSIGNED_INT_SAMPLER_3D
				|| type == GL20.GL_SAMPLER_3D
				|| type == GL20.GL_SAMPLER_1D_SHADOW
				|| type == GL20.GL_SAMPLER_2D_SHADOW;
	}

	private static boolean isImage(int type) {
		return type == ARBShaderImageLoadStore.GL_IMAGE_1D
			|| type == ARBShaderImageLoadStore.GL_IMAGE_2D
			|| type == ARBShaderImageLoadStore.GL_UNSIGNED_INT_IMAGE_1D
			|| type == ARBShaderImageLoadStore.GL_UNSIGNED_INT_IMAGE_2D
			|| type == ARBShaderImageLoadStore.GL_UNSIGNED_INT_IMAGE_3D
			|| type == ARBShaderImageLoadStore.GL_INT_IMAGE_1D
			|| type == ARBShaderImageLoadStore.GL_INT_IMAGE_2D
			|| type == ARBShaderImageLoadStore.GL_INT_IMAGE_3D
			|| type == ARBShaderImageLoadStore.GL_IMAGE_3D
			|| type == ARBShaderImageLoadStore.GL_IMAGE_1D_ARRAY
			|| type == ARBShaderImageLoadStore.GL_IMAGE_2D_ARRAY;
	}
}
