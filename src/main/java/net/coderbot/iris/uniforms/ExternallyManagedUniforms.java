package net.coderbot.iris.uniforms;

import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformType;

public class ExternallyManagedUniforms {
	private ExternallyManagedUniforms() {
		// no construction allowed
	}

	public static void addExternallyManagedUniforms(UniformHolder uniformHolder) {
		addMat4(uniformHolder, "iris_ModelViewMatrix");
		addMat4(uniformHolder, "u_ModelViewProjectionMatrix");
		addMat4(uniformHolder, "iris_NormalMatrix");
		uniformHolder.externallyManagedUniform("heavyFog", UniformType.BOOL);
	}

	public static void addExternallyManagedUniforms116(UniformHolder uniformHolder) {
		addExternallyManagedUniforms(uniformHolder);

		uniformHolder.externallyManagedUniform("u_ModelScale", UniformType.VEC3);
		uniformHolder.externallyManagedUniform("u_TextureScale", UniformType.VEC2);
	}

	private static void addMat4(UniformHolder uniformHolder, String name) {
		uniformHolder.externallyManagedUniform(name, UniformType.MAT4);
	}
}
