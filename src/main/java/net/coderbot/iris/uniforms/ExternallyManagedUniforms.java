package net.coderbot.iris.uniforms;

import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformType;

public class ExternallyManagedUniforms {
	private ExternallyManagedUniforms() {
		// no construction allowed
	}

	public static void addExternallyManagedUniforms(UniformHolder uniformHolder) {
		addMat4(uniformHolder, "iris_ModelViewMatrix");
		addMat4(uniformHolder, "iris_ModelViewMatrixInverse");
		addMat4(uniformHolder, "iris_ProjectionMatrix");
		addMat4(uniformHolder, "iris_ProjectionMatrixInverse");
		addMat4(uniformHolder, "iris_LightmapTextureMatrix");
		addMat4(uniformHolder, "u_ModelViewProjectionMatrix");
		addMat3(uniformHolder, "iris_NormalMatrix");
		uniformHolder.externallyManagedUniform("heavyFog", UniformType.BOOL);
		uniformHolder.externallyManagedUniform("angelica_ClipPlanesEnabled", UniformType.BOOL);
		uniformHolder.externallyManagedUniform("angelica_ClipPlane[0]", UniformType.VEC4);
	}

	public static void addExternallyManagedUniforms116(UniformHolder uniformHolder) {
		addExternallyManagedUniforms(uniformHolder);

		uniformHolder.externallyManagedUniform("u_ModelScale", UniformType.VEC3);
		uniformHolder.externallyManagedUniform("u_TextureScale", UniformType.VEC2);
		uniformHolder.externallyManagedUniform("u_RegionOffset", UniformType.VEC3);
	}

	private static void addMat3(UniformHolder uniformHolder, String name) {
		uniformHolder.externallyManagedUniform(name, UniformType.MAT3);
	}

	private static void addMat4(UniformHolder uniformHolder, String name) {
		uniformHolder.externallyManagedUniform(name, UniformType.MAT4);
	}
}
