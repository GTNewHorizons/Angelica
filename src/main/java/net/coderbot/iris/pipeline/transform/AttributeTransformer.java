package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements AttributeShaderTransformer using glsl-transformer AST
 * transformation methods.
 */
class AttributeTransformer {
	public static void transform(Transformer transformer, AttributeParameters parameters, int version) {
		if (parameters.scrollGlint && parameters.type == ShaderType.VERTEX) {
			transformer.replaceExpression("gl_MultiTexCoord0", "(gl_TextureMatrix[0] * gl_MultiTexCoord0)", GLSLParser::postfix_expression);
		}

		// Always core profile — minimum GLSL version is 330 (see ShaderTransformer.getStageMinimumVersion)
		CommonTransformer.transform(transformer, parameters, true, version);

		// Entity ID and overlay color patching (uniform path)
		EntityPatcher.patchEntityId(transformer, parameters);
		EntityPatcher.patchOverlayColor(transformer, parameters);

		transformCore(transformer, parameters);
	}

	private static void transformCore(Transformer transformer, AttributeParameters parameters) {
		final boolean instancedVertex = parameters.instanced && parameters.type == ShaderType.VERTEX;
		final boolean wantsMvInverse = instancedVertex && transformer.containsCall("gl_ModelViewMatrixInverse");
		CoreTransformHelper.injectMatrixUniforms(transformer, instancedVertex);

		aliasIfUsed(transformer, "projectionMatrix", "iris_ProjectionMatrix");
		aliasIfUsed(transformer, "modelViewMatrix", "iris_ModelViewMatrix");
		aliasIfUsed(transformer, "normalMatrix", "iris_NormalMatrix");

		if (parameters.type == ShaderType.VERTEX) {
			transformer.injectVariable("layout(location = 0) in vec4 iris_Vertex;");
			transformer.injectVariable("layout(location = 1) in vec4 iris_Color;");
			transformer.injectVariable("layout(location = 2) in vec4 iris_MultiTexCoord0;");
			if (instancedVertex) {
				transformer.injectVariable("vec4 iris_MultiTexCoord1;");
			} else {
				transformer.injectVariable("layout(location = 3) in vec4 iris_MultiTexCoord1;");
			}
			transformer.injectVariable("layout(location = 4) in vec3 iris_Normal;");

			transformer.rename("gl_Vertex", "iris_Vertex");
			transformer.replaceExpression("gl_Color", instancedVertex ? "(iris_Color * iris_ColorModulator * iris_InstColor)" : "(iris_Color * iris_ColorModulator)");
			transformer.rename("gl_Normal", "iris_Normal");
			aliasIfUsed(transformer, "vaNormal", "iris_Normal");
			if (transformer.containsCall("vaPosition") && !transformer.hasVariable("vaPosition")) {
				transformer.replaceExpression("vaPosition", "iris_Vertex.xyz");
			}

			// ftransform() = gl_ModelViewProjectionMatrix * gl_Vertex
			transformer.renameFunctionCall("ftransform", "iris_ftransform");
			transformer.injectFunction("vec4 iris_ftransform() { return (iris_ProjectionMatrix * iris_ModelViewMatrix) * iris_Vertex; }");

			// gl_MultiTexCoord1 and gl_MultiTexCoord2 are both lightmap
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

			// Rename remaining gl_MultiTexCoord references to iris_ versions
			transformer.rename("gl_MultiTexCoord0", "iris_MultiTexCoord0");
			transformer.rename("gl_MultiTexCoord1", "iris_MultiTexCoord1");

			if (transformer.hasVariable("gl_MultiTexCoord3") && !transformer.hasVariable("mc_midTexCoord")) {
				transformer.rename("gl_MultiTexCoord3", "mc_midTexCoord");
				transformer.injectVariable("in vec4 mc_midTexCoord;");
			}

			if (instancedVertex) {
				foldConstantAttribute(transformer, "mc_Entity", "-1.0", "vec2(-1.0, -1.0)", "vec3(-1.0, -1.0, 0.0)", "vec4(-1.0, -1.0, 0.0, 1.0)");

				final StringBuilder init = new StringBuilder(256);
				init.append("{ iris_ModelViewMatrix = mat4(iris_InstMat0, iris_InstMat1, iris_InstMat2, iris_InstMat3);");
				init.append(" iris_NormalMatrix = mat3(normalize(iris_InstMat0.xyz), normalize(iris_InstMat1.xyz), normalize(iris_InstMat2.xyz));");
				if (wantsMvInverse) {
					init.append(" iris_ModelViewMatrixInverse = inverse(iris_ModelViewMatrix);");
				}
				init.append(" iris_MultiTexCoord1 = vec4(iris_InstLightmap, 0.0, 1.0); }");
				transformer.prependMain(init.toString());
			}
		}
	}

	private static void foldConstantAttribute(Transformer transformer, String name, String floatVal, String vec2Val, String vec3Val, String vec4Val) {
		final int type = transformer.findType(name);
		if (type == 0) {
			return;
		}
		final String replacement = switch (type) {
			case GLSLLexer.FLOAT -> floatVal;
			case GLSLLexer.VEC2 -> vec2Val;
			case GLSLLexer.VEC3 -> vec3Val;
			case GLSLLexer.VEC4 -> vec4Val;
			default -> throw new IllegalStateException("Unsupported " + name + " type token " + type + " for instanced variant");
		};
		transformer.removeVariable(name);
		transformer.replaceExpression(name, replacement);
	}

	private static void aliasIfUsed(Transformer transformer, String modernName, String irisName) {
		if (transformer.containsCall(modernName) && !transformer.hasVariable(modernName)) {
			transformer.rename(modernName, irisName);
		}
	}
}
