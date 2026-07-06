package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.HashSet;
import java.util.Set;

public class CommonTransformer {

	public static void transform(Transformer root, Parameters parameters, boolean core, int glslVersion) {
		root.rename("gl_FogFragCoord", "iris_FogFragCoord");
		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("out float iris_FogFragCoord;");
			root.prependMain("iris_FogFragCoord = 0.0f;");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in float iris_FogFragCoord;");
		}

		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("uniform vec4 iris_ColorModulator;");
			root.injectVariable("out vec4 iris_FrontColor;");
			root.rename("gl_FrontColor", "iris_FrontColor");
			root.prependMain("iris_FrontColor = iris_ColorModulator;");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in vec4 iris_FrontColor;");
			root.rename("gl_Color", "iris_FrontColor");
		}

		if (root.hasVariable("gl_TexCoord")) {
			if (parameters.type == ShaderType.VERTEX) {
				root.rename("gl_TexCoord", "irs_texCoords");
				root.injectVariable("out vec4 irs_texCoords[3];");
			} else if (parameters.type == ShaderType.FRAGMENT) {
				root.rename("gl_TexCoord", "irs_texCoords");
				root.injectVariable("in vec4 irs_texCoords[3];");
			}
		}

		if (parameters.type == ShaderType.FRAGMENT) {
			if (root.containsCall("gl_FragColor")) {
				root.replaceExpression("gl_FragColor", "gl_FragData[0]");
			}

			if (core) {
				// Core profile: gl_FragData doesn't exist. Flatten gl_FragData[N] → iris_FragDataN with layout-qualified out declarations.
				Set<Integer> found = new HashSet<>();
				root.renameArray("gl_FragData", "iris_FragData", found);

				for (Integer i : found) {
					root.injectVariable("layout (location = " + i + ") out vec4 iris_FragData" + i + ";");
				}

				// Core profile: GL_ALPHA_TEST is removed. 1.7.10 engine relies on alpha test to discard transparent fragments. Inject
				// runtime discard using the GLSM-tracked alpha reference value.
				if (found.contains(0) && parameters.patch != Patch.COMPOSITE && parameters.patch != Patch.COMPUTE) {
					root.injectVariable("uniform float iris_currentAlphaTest;");
                    root.injectVariable("uniform int iris_currentAlphaFunc;");
                    root.injectFunction(
                        "bool iris_alphaTestPass(float a) {" +
                            " if (iris_currentAlphaFunc == 7) return true;" +   // ALWAYS / disabled
                            " if (iris_currentAlphaFunc == 0) return false;" +  // NEVER
                            " float qa = round(a * 255.0);" +
                            " float qr = round(iris_currentAlphaTest * 255.0);" +
                            " if (iris_currentAlphaFunc == 1) return qa < qr;" +    // LESS
                            " if (iris_currentAlphaFunc == 2) return qa == qr;" +   // EQUAL
                            " if (iris_currentAlphaFunc == 3) return qa <= qr;" +   // LEQUAL
                            " if (iris_currentAlphaFunc == 4) return qa > qr;" +    // GREATER
                            " if (iris_currentAlphaFunc == 5) return qa != qr;" +   // NOTEQUAL
                            " return qa >= qr;" +                                // GEQUAL (6)
                            "}"
                    );
                    root.appendMain("if (!iris_alphaTestPass(iris_FragData0.a)) discard;");
				}
			}
		}

		if (root.containsCall("texture") && root.hasVariable("texture")) {
			root.rename("texture", "gtexture");
		}

		if (root.hasVariable("angelica_renamed_texture")) {
			root.rename("angelica_renamed_texture", "gtexture");
		}

		if (root.containsCall("gcolor") && root.hasVariable("gcolor")) {
			root.rename("gcolor", "gtexture");
		}

		root.rename("gl_Fog", "iris_Fog");
		root.injectVariable("uniform float iris_FogDensity;");
		root.injectVariable("uniform float iris_FogStart;");
		root.injectVariable("uniform float iris_FogEnd;");
		root.injectVariable("uniform vec4 iris_FogColor;");
		root.injectFunction("struct iris_FogParameters {vec4 color;float density;float start;float end;float scale;};");
		root.injectFunction("iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, iris_FogStart, iris_FogEnd, 1.0f / (iris_FogEnd - iris_FogStart));");

		root.renameFunctionCall(GlslTransformUtils.TEXTURE_RENAMES);
		root.renameAndWrapShadow("shadow2D", "texture");
		root.renameAndWrapShadow("shadow2DLod", "textureLod");

		if (root.containsCall("textureLodOffset")) {
			root.injectFunction("vec4 iris_textureLodOffset(sampler2D iris_tlo_s, vec2 iris_tlo_c, float iris_tlo_l, ivec2 iris_tlo_o) { return textureLod(iris_tlo_s, iris_tlo_c + vec2(iris_tlo_o) / vec2(textureSize(iris_tlo_s, int(iris_tlo_l))), iris_tlo_l); }");
			root.injectFunction("float iris_textureLodOffset(sampler2DShadow iris_tlo_s, vec3 iris_tlo_c, float iris_tlo_l, ivec2 iris_tlo_o) { return textureLod(iris_tlo_s, vec3(iris_tlo_c.xy + vec2(iris_tlo_o) / vec2(textureSize(iris_tlo_s, int(iris_tlo_l))), iris_tlo_c.z), iris_tlo_l); }");
			root.injectFunction("vec4 iris_textureLodOffset(sampler3D iris_tlo_s, vec3 iris_tlo_c, float iris_tlo_l, ivec3 iris_tlo_o) { return textureLod(iris_tlo_s, iris_tlo_c + vec3(iris_tlo_o) / vec3(textureSize(iris_tlo_s, int(iris_tlo_l))), iris_tlo_l); }");
			root.injectFunction("vec4 iris_textureLodOffset(sampler2DArray iris_tlo_s, vec3 iris_tlo_c, float iris_tlo_l, ivec2 iris_tlo_o) { return textureLod(iris_tlo_s, vec3(iris_tlo_c.xy + vec2(iris_tlo_o) / vec2(textureSize(iris_tlo_s, int(iris_tlo_l)).xy), iris_tlo_c.z), iris_tlo_l); }");
			root.injectFunction("ivec4 iris_textureLodOffset(isampler2D iris_tlo_s, vec2 iris_tlo_c, float iris_tlo_l, ivec2 iris_tlo_o) { return textureLod(iris_tlo_s, iris_tlo_c + vec2(iris_tlo_o) / vec2(textureSize(iris_tlo_s, int(iris_tlo_l))), iris_tlo_l); }");
			root.injectFunction("uvec4 iris_textureLodOffset(usampler2D iris_tlo_s, vec2 iris_tlo_c, float iris_tlo_l, ivec2 iris_tlo_o) { return textureLod(iris_tlo_s, iris_tlo_c + vec2(iris_tlo_o) / vec2(textureSize(iris_tlo_s, int(iris_tlo_l))), iris_tlo_l); }");
			root.renameFunctionCall("textureLodOffset", "iris_textureLodOffset");
		}

		if (parameters.type == ShaderType.VERTEX) {
			root.mutateTree(tree -> {
				if (tree.children != null) {
					tree.children.removeIf(child ->
						child instanceof GLSLParser.External_declarationContext
							&& ((GLSLParser.External_declarationContext) child).declaration() != null
							&& child.getText().contains("gl_PerVertex"));
				}
			});
		}

		if (parameters.patch == Patch.ATTRIBUTES && parameters.type == ShaderType.VERTEX) {
			root.injectVariable("uniform bool angelica_ClipPlanesEnabled;");
			root.injectVariable("uniform vec4 angelica_ClipPlane[8];");
			root.appendMain(
				"{ if (angelica_ClipPlanesEnabled) { vec4 _cp_ep = iris_ModelViewMatrix * iris_Vertex; "
				+ "gl_ClipDistance[0] = dot(angelica_ClipPlane[0], _cp_ep); "
				+ "gl_ClipDistance[1] = dot(angelica_ClipPlane[1], _cp_ep); "
				+ "gl_ClipDistance[2] = dot(angelica_ClipPlane[2], _cp_ep); "
				+ "gl_ClipDistance[3] = dot(angelica_ClipPlane[3], _cp_ep); "
				+ "gl_ClipDistance[4] = dot(angelica_ClipPlane[4], _cp_ep); "
				+ "gl_ClipDistance[5] = dot(angelica_ClipPlane[5], _cp_ep); "
				+ "gl_ClipDistance[6] = dot(angelica_ClipPlane[6], _cp_ep); "
				+ "gl_ClipDistance[7] = dot(angelica_ClipPlane[7], _cp_ep); } }"
			);
		}
	}
}
