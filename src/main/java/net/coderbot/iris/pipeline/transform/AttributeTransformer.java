package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.Util;

/**
 * Implements AttributeShaderTransformer using glsl-transformer AST
 * transformation methods.
 */
class AttributeTransformer {
	public static void transform(GLSLParser.Translation_unitContext translationUnit, AttributeParameters parameters, String profile, int version) {
		boolean isCore = profile.equals("core") || (version > 140 && !profile.equals("compatibility"));
		if (isCore) {
			if (parameters.type == PatchShaderType.VERTEX) {
				throw new IllegalStateException("Vertex shaders must be in the compatibility profile to run properly!");
			}
			return;
		}

		// gl_MultiTexCoord1 and gl_MultiTexCoord2 are both ways to refer to the
		// lightmap texture coordinate.
		// See https://github.com/IrisShaders/Iris/issues/1149
		if (parameters.inputs.lightmap) {
			Util.rename(translationUnit, "gl_MultiTexCoord2", "gl_MultiTexCoord1");
		}

        if (!parameters.inputs.lightmap) {
            Util.replaceExpression(translationUnit, "gl_MultiTexCoord1", "vec4(240.0, 240.0, 0.0, 1.0)");
            Util.replaceExpression(translationUnit, "gl_MultiTexCoord2", "vec4(240.0, 240.0, 0.0, 1.0)");
        }
        if (!parameters.inputs.texture) {
            Util.replaceExpression(translationUnit, "gl_MultiTexCoord0", "vec4(240.0, 240.0, 0.0, 1.0)");
        }

		patchTextureMatrices(translationUnit, parameters.inputs.lightmap);

		if (parameters.type.glShaderType == ShaderType.VERTEX
				&& Util.hasVariable(translationUnit, "gl_MultiTexCoord3")
				&& !Util.hasVariable(translationUnit, "mc_midTexCoord")) {
			// TODO: proper type conversion
			// gl_MultiTexCoord3 is a super legacy alias of mc_midTexCoord. We don't do this
			// replacement if we think mc_midTexCoord could be defined just we can't handle
			// an existing declaration robustly. But basically the proper way to do this is
			// to define mc_midTexCoord only if it's not defined, and if it is defined,
			// figure out its type, then replace all occurrences of gl_MultiTexCoord3 with
			// the correct conversion from mc_midTexCoord's declared type to vec4.
            Util.rename(translationUnit, "gl_MultiTexCoord3", "mc_midTexCoord");
			Util.injectVariable(translationUnit, "attribute vec4 mc_midTexCoord;");
		}
	}

	private static void patchTextureMatrices(GLSLParser.Translation_unitContext translationUnit, boolean hasLightmap) {
        Util.rename(translationUnit, "gl_TextureMatrix", "iris_TextureMatrix");

        // TODO: These were originally marked const, but that breaks things
        // If these are marked const, then using them below in the iris_LightmapTextureMatrix creation
        // breaks with them being undefined. I suppose the const variables cause them to get defined later than otherwise?
        // Similarly, if you make them a const, then iris_ONE_OVER_32 breaks because it references iris_ONE_OVER_256.
        // Not sure if it really matters if these are marked const or not. Alternatively they can be marked const
        // and the values hardcoded within the places that use them here, that seems to work fine, but this probably
        // stayed closer to the original way it worked.
        Util.injectVariable(translationUnit, "float iris_ONE_OVER_256 = 0.00390625;");
        Util.injectVariable(translationUnit, "float iris_ONE_OVER_32 = iris_ONE_OVER_256 * 8;");

        if (hasLightmap) {
            Util.injectVariable(translationUnit, "mat4 iris_LightmapTextureMatrix = gl_TextureMatrix[1];");
        } else {
            Util.injectVariable(translationUnit, "mat4 iris_LightmapTextureMatrix =" +
                "mat4(iris_ONE_OVER_256, 0.0, 0.0, 0.0," +
                "     0.0, iris_ONE_OVER_256, 0.0, 0.0," +
                "     0.0, 0.0, iris_ONE_OVER_256, 0.0," +
                "     iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_256);");
        }

        // column major
        Util.injectVariable(translationUnit, "mat4 iris_TextureMatrix[8] = mat4[8](" +
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
