package net.coderbot.iris.uniforms.builtin;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import org.joml.Matrix4f;

public class BuiltinReplacementUniforms {
	private static final Matrix4f lightmapTextureMatrix;

	static {
		// This mimics the transformations done in LightmapTextureManager to the GL_TEXTURE matrix.
		lightmapTextureMatrix = new Matrix4f();
		lightmapTextureMatrix.identity();
		lightmapTextureMatrix.scale(0.00390625f);

        // TODO: Iris-Shaders - Is this logic correct?
        final Matrix4f translateMatrix = new Matrix4f();
        translateMatrix.translate(8.0f, 8.0f, 8.0f);
		lightmapTextureMatrix.mul(translateMatrix);
	}

	public static void addBuiltinReplacementUniforms(UniformHolder uniforms) {
		uniforms.uniformMatrix(UniformUpdateFrequency.ONCE, "iris_LightmapTextureMatrix", () -> {
			Iris.logger.warn("A shader appears to require the lightmap texture matrix even after transformations have occurred");
			Iris.logger.warn("Iris handles this correctly but it indicates that the shader is doing weird things with lightmap coordinates");

			return lightmapTextureMatrix;
		});
	}
}
