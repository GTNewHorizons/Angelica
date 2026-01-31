package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.taumc.glsl.Transformer;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements AttributeShaderTransformer using glsl-transformer AST
 * transformation methods.
 */
class AttributeTransformer {
	public static void transform(Transformer transformer, AttributeParameters parameters, String profile, int version) {
		boolean isCore = profile.equals("core") || (version > 140 && !profile.equals("compatibility"));

		// Common patches (compatibility profile)
		CommonTransformer.transform(transformer, parameters, isCore, version);

		// Entity ID and overlay color patching (uniform path)
		EntityPatcher.patchEntityId(transformer, parameters);
		EntityPatcher.patchOverlayColor(transformer, parameters);

		if (isCore) {
			if (parameters.type == ShaderType.VERTEX) {
				throw new IllegalStateException("Vertex shaders must be in the compatibility profile to run properly!");
			}
			return;
		}

		// gl_MultiTexCoord1 and gl_MultiTexCoord2 are both ways to refer to the
		// lightmap texture coordinate.
		// See https://github.com/IrisShaders/Iris/issues/1149
		if (parameters.inputs.lightmap) {
			transformer.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");
		}

		Map<String, String> texCoordReplacements = new HashMap<>();
		if (!parameters.inputs.lightmap) {
			texCoordReplacements.put("gl_MultiTexCoord1", "vec4(240.0, 240.0, 0.0, 1.0)");
			texCoordReplacements.put("gl_MultiTexCoord2", "vec4(240.0, 240.0, 0.0, 1.0)");
		}
		if (!parameters.inputs.texture) {
			texCoordReplacements.put("gl_MultiTexCoord0", "vec4(240.0, 240.0, 0.0, 1.0)");
		}
		texCoordReplacements.forEach(transformer::replaceExpression);

		patchTextureMatrices(transformer, parameters.inputs.lightmap);

		if (parameters.type == ShaderType.VERTEX && transformer.hasVariable("gl_MultiTexCoord3") && !transformer.hasVariable("mc_midTexCoord")) {
			transformer.rename("gl_MultiTexCoord3", "mc_midTexCoord");
			transformer.injectVariable("attribute vec4 mc_midTexCoord;");
		}
	}

	private static void patchTextureMatrices(Transformer transformer, boolean hasLightmap) {
		transformer.rename("gl_TextureMatrix", "iris_TextureMatrix");

		transformer.injectVariable("float iris_ONE_OVER_256 = 0.00390625;");
		transformer.injectVariable("float iris_ONE_OVER_32 = iris_ONE_OVER_256 * 8;");

		if (hasLightmap) {
			transformer.injectVariable("mat4 iris_LightmapTextureMatrix = gl_TextureMatrix[1];");
		} else {
			transformer.injectVariable("mat4 iris_LightmapTextureMatrix =" +
				"mat4(iris_ONE_OVER_256, 0.0, 0.0, 0.0," +
				"     0.0, iris_ONE_OVER_256, 0.0, 0.0," +
				"     0.0, 0.0, iris_ONE_OVER_256, 0.0," +
				"     iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_256);");
		}

		transformer.injectVariable("mat4 iris_TextureMatrix[8] = mat4[8](" +
			"gl_TextureMatrix[0]," +
			"iris_LightmapTextureMatrix," +
			"mat4(1.0)," +
			"mat4(1.0)," +
			"mat4(1.0)," +
			"mat4(1.0)," +
			"mat4(1.0)," +
			"mat4(1.0)" +
			");");
	}
}
