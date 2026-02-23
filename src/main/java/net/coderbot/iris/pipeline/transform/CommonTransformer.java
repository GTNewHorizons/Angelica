package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

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
			root.injectVariable("out vec4 iris_FrontColor;");
			root.rename("gl_FrontColor", "iris_FrontColor");
			root.prependMain("iris_FrontColor = vec4(1.0);");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in vec4 iris_FrontColor;");
			root.rename("gl_Color", "iris_FrontColor");
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
					root.appendMain("if (iris_FragData0.a <= iris_currentAlphaTest) discard;");
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
	}
}
