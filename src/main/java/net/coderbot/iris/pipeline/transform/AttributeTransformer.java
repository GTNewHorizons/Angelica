package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedGlslHelpers;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import net.coderbot.iris.gl.shader.ProgramCreator;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
		final boolean vertex = parameters.type == ShaderType.VERTEX;
		final boolean instancedVertex = vertex && parameters.instancing != Instancing.NONE;
		final boolean cubeVertex = vertex && parameters.instancing == Instancing.CUBE;
		final boolean particleVertex = vertex && parameters.instancing == Instancing.PARTICLE;
		final boolean weatherVertex = vertex && parameters.instancing == Instancing.WEATHER;
		final boolean lineVertex = vertex && parameters.wideLines;
		final boolean matrixVertex = vertex && parameters.instancing.hasInstanceHead();
		final Set<String> declaredInterface = declaredInterface(transformer);
		CoreTransformHelper.injectMatrixUniforms(transformer, vertex ? parameters.instancing : Instancing.NONE);

		CORE_PROFILE_MATRICES.forEach((name, irisName) -> {
			if (claimCoreProfileName(transformer, declaredInterface, name)) {
				transformer.rename(name, irisName);
			}
		});
		final boolean wantsMvInverse = matrixVertex && transformer.containsCall("iris_ModelViewMatrixInverse");

		if (parameters.type == ShaderType.VERTEX) {
			mapCoreProfileInputs(transformer, declaredInterface);

			if (!particleVertex && !weatherVertex) {
				if (lineVertex) {
					transformer.injectVariable("layout(location = 0) in vec4 iris_LineVertex;");
					transformer.injectVariable("vec4 iris_Vertex = vec4(0.0, 0.0, 0.0, 1.0);");
				} else {
					transformer.injectVariable("layout(location = 0) in vec4 iris_Vertex;");
				}
				transformer.injectVariable("layout(location = 1) in vec4 iris_Color;");
				if (cubeVertex) {
					transformer.injectVariable("vec4 iris_MultiTexCoord0;");
				} else {
					transformer.injectVariable("layout(location = " + PRIMARY_UV + ") in vec4 iris_MultiTexCoord0;");
				}
				if (instancedVertex) {
					transformer.injectVariable("vec4 iris_MultiTexCoord1;");
				} else {
					transformer.injectVariable("layout(location = " + SECONDARY_UV + ") in vec4 iris_MultiTexCoord1;");
				}
			}
			if (lineVertex) {
				transformer.injectVariable("layout(location = 4) in vec3 iris_LineDirection;");
				transformer.injectVariable("const vec3 iris_Normal = vec3(0.0, 0.0, 1.0);");
			} else {
				transformer.injectVariable("layout(location = 4) in vec3 iris_Normal;");
			}

			transformer.rename("gl_Vertex", "iris_Vertex");
			transformer.replaceExpression("gl_Color", matrixVertex ? "(iris_Color * iris_ColorModulator * iris_InstColor)" : "(iris_Color * iris_ColorModulator)");
			transformer.rename("gl_Normal", "iris_Normal");

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

				final StringBuilder init = new StringBuilder(256).append("{ ");
				if (particleVertex) {
					init.append("iris_Vertex = ").append(InstancedGlslHelpers.particlePos("iris_InstCenterHalf", "iris_ParticleOffset")).append(';');
					init.append(" iris_Color = iris_InstColor;");
					init.append(" iris_MultiTexCoord0 = ").append(InstancedGlslHelpers.particleUv("iris_InstUv", "iris_ParticleCorner")).append(';');
					init.append(" iris_MultiTexCoord1 = vec4(iris_InstLightmap, 0.0, 1.0);");
					assignMidTexCoord(transformer, init, "vec4((iris_InstUv.x + iris_InstUv.z) * 0.5, (iris_InstUv.y + iris_InstUv.w) * 0.5, 0.0, 1.0)");
				} else if (weatherVertex) {
					init.append(InstancedGlslHelpers.weatherPrologue("iris_", "iris_", "iris_WeatherCorner",
						"iris_Vertex", "iris_Color", "iris_MultiTexCoord0", "iris_MultiTexCoord1", " "));
					assignMidTexCoord(transformer, init, "vec4(0.5, 0.5, 0.0, 1.0)");
				} else {
					init.append("iris_ModelViewMatrix = ").append(InstancedGlslHelpers.mat4FromRows("iris_InstRow0", "iris_InstRow1", "iris_InstRow2")).append(';');
					init.append(" iris_NormalMatrix = ").append(InstancedGlslHelpers.mat3FromRows("iris_InstRow0", "iris_InstRow1", "iris_InstRow2")).append(';');
					if (wantsMvInverse) {
						init.append(" iris_ModelViewMatrixInverse = inverse(iris_ModelViewMatrix);");
					}
					init.append(" iris_MultiTexCoord1 = vec4(").append(cubeVertex ? "iris_InstLightmapScale.xy" : "iris_InstLightmap").append(", 0.0, 1.0);");
					if (cubeVertex) {
						appendCubeTexCoords(transformer, init);
					}
				}
				init.append(" }");
				transformer.prependMain(init.toString());
			}
		}

		if (parameters.wideLines) {
			widenLines(transformer, vertex);
		}
	}

	private static void widenLines(Transformer transformer, boolean vertex) {
		transformer.injectVariable("const mat4 iris_VIEW_SCALE = mat4(mat3(1.0 - (1.0 / 256.0)));");
		transformer.replaceExpression("iris_ModelViewMatrix", "(iris_VIEW_SCALE * iris_ModelViewMatrix)", GLSLParser::postfix_expression);
		if (!vertex) {
			return;
		}

		transformer.injectVariable("uniform float iris_LineWidth;");
		transformer.injectVariable("uniform vec2 iris_ScreenSize;");
		transformer.rename("main", "irisMain");
		transformer.injectAtEnd("void iris_widen_lines(vec4 linePosStart, vec4 linePosEnd) {"
			+ " vec3 ndc1 = linePosStart.xyz / linePosStart.w;"
			+ " vec3 ndc2 = linePosEnd.xyz / linePosEnd.w;"
			+ " vec2 lineScreenDirection = normalize((ndc2.xy - ndc1.xy) * iris_ScreenSize);"
			+ " vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x) * iris_LineWidth / iris_ScreenSize;"
			+ " if (lineOffset.x < 0.0) { lineOffset *= -1.0; }"
			+ " if (gl_VertexID % 2 == 0) {"
			+ " gl_Position = vec4((ndc1 + vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);"
			+ " } else {"
			+ " gl_Position = vec4((ndc1 - vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);"
			+ " } }");
		transformer.injectAtEnd("void main() {"
			+ " iris_Vertex = iris_LineVertex + vec4(iris_LineDirection, 0.0);"
			+ " irisMain();"
			+ " vec4 linePosEnd = gl_Position;"
			+ " gl_Position = vec4(0.0);"
			+ " iris_Vertex = iris_LineVertex;"
			+ " irisMain();"
			+ " vec4 linePosStart = gl_Position;"
			+ " iris_widen_lines(linePosStart, linePosEnd); }");
	}

	private static final int PRIMARY_UV = VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation();
	private static final int SECONDARY_UV = VertexFormatElement.Usage.SECONDARY_UV.getAttributeLocation();

	private static void appendCubeTexCoords(Transformer transformer, StringBuilder init) {
		init.append(' ').append(InstancedGlslHelpers.cubePrelude("iris_", "iris_CubeMid", "iris_CubeDelta", "iris_Normal", "iris_CubeTex", " "));
		init.append(" iris_MultiTexCoord0 = ").append(InstancedGlslHelpers.cubeUv("iris_CubeTex", "iris_InstLightmapScale", "iris_cubeU.x", "iris_cubeU.y", "iris_cubeV.x", "iris_cubeV.y")).append(';');

		final int tangentType = transformer.findType("at_tangent");
		if (tangentType != 0) {
			final String name = vecName(tangentType);
			if (name == null || tangentType == GLSLLexer.VEC2) {
				throw new IllegalStateException("Unsupported at_tangent type token " + tangentType + " for the cube instanced variant");
			}
			transformer.removeVariable("at_tangent");
			transformer.injectVariable("layout(location = " + ProgramCreator.AT_TANGENT + ") in vec4 iris_CubeTangent;");
			transformer.injectVariable(name + " at_tangent;");
			init.append(" at_tangent = (iris_CubeTangent * (1.0 - 2.0 * iris_cubeMirror))").append(swizzle(tangentType)).append(';');
		}

		assignMidTexCoord(transformer, init, InstancedGlslHelpers.cubeUv("iris_CubeTex", "iris_InstLightmapScale", "iris_cubeMidU.x", "iris_cubeMidU.y", "iris_CubeMid.z", "iris_CubeMid.w"));
	}

	private static void assignMidTexCoord(Transformer transformer, StringBuilder init, String midExpression) {
		final int midType = transformer.findType("mc_midTexCoord");
		if (midType == 0) {
			return;
		}
		final String name = vecName(midType);
		if (name == null) {
			throw new IllegalStateException("Unsupported mc_midTexCoord type token " + midType + " for the instanced variant");
		}
		transformer.removeVariable("mc_midTexCoord");
		transformer.injectVariable(name + " mc_midTexCoord;");
		init.append(" mc_midTexCoord = (").append(midExpression).append(')').append(swizzle(midType)).append(';');
	}

	private static String vecName(int type) {
		return switch (type) {
			case GLSLLexer.VEC2 -> "vec2";
			case GLSLLexer.VEC3 -> "vec3";
			case GLSLLexer.VEC4 -> "vec4";
			default -> null;
		};
	}

	private static String swizzle(int type) {
		return switch (type) {
			case GLSLLexer.VEC2 -> ".xy";
			case GLSLLexer.VEC3 -> ".xyz";
			default -> "";
		};
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

	private static final Map<String, String> CORE_PROFILE_MATRICES = Map.of(
		"projectionMatrix", "iris_ProjectionMatrix",
		"projectionMatrixInverse", "iris_ProjectionMatrixInverse",
		"modelViewMatrix", "iris_ModelViewMatrix",
		"modelViewMatrixInverse", "iris_ModelViewMatrixInverse",
		"normalMatrix", "iris_NormalMatrix",
		"textureMatrix", "iris_TextureMatrix");

	private static Set<String> declaredInterface(Transformer transformer) {
		final Set<String> names = new HashSet<>(transformer.findQualifiers(GLSLLexer.UNIFORM).keySet());
		names.addAll(transformer.findQualifiers(GLSLLexer.IN).keySet());
		names.addAll(transformer.findQualifiers(GLSLLexer.ATTRIBUTE).keySet());
		return names;
	}

	private static boolean claimCoreProfileName(Transformer transformer, Set<String> declaredInterface, String name) {
		if (!transformer.containsCall(name)) {
			return false;
		}
		if (declaredInterface.contains(name)) {
			transformer.removeVariable(name);
			return true;
		}
		return !transformer.hasVariable(name);
	}

	private static void mapCoreProfileInputs(Transformer transformer, Set<String> declaredInterface) {
		final boolean overlayIsInt = transformer.findType("vaUV1") == GLSLLexer.IVEC2;
		final boolean lightIsInt = transformer.findType("vaUV2") == GLSLLexer.IVEC2;
		mapCoreProfileInput(transformer, declaredInterface, "vaPosition", "gl_Vertex.xyz");
		mapCoreProfileInput(transformer, declaredInterface, "vaColor", "gl_Color");
		mapCoreProfileInput(transformer, declaredInterface, "vaNormal", "gl_Normal");
		mapCoreProfileInput(transformer, declaredInterface, "vaUV0", "gl_MultiTexCoord0.xy");
		// There is no overlay attribute (entityColor carries the hurt tint), so every vertex reads "no overlay"
		mapCoreProfileInput(transformer, declaredInterface, "vaUV1", overlayIsInt ? "ivec2(0, 10)" : "vec2(0.0, 10.0)");
		mapCoreProfileInput(transformer, declaredInterface, "vaUV2", lightIsInt ? "ivec2(gl_MultiTexCoord1.xy)" : "gl_MultiTexCoord1.xy");
	}

	private static void mapCoreProfileInput(Transformer transformer, Set<String> declaredInterface, String name, String replacement) {
		if (claimCoreProfileName(transformer, declaredInterface, name)) {
			transformer.replaceExpression(name, replacement, GLSLParser::postfix_expression);
		}
	}
}
