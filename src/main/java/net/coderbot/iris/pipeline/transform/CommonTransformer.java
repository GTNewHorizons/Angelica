package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.blending.AlphaTest;
import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

import java.util.Map;

public class CommonTransformer {

	private static final Map<String, String> COMMON_TEXTURE_RENAMES = Map.ofEntries(
		Map.entry("texture2D", "texture"),
		Map.entry("texture3D", "texture"),
		Map.entry("texture2DLod", "textureLod"),
		Map.entry("texture3DLod", "textureLod"),
		Map.entry("texture2DProj", "textureProj"),
		Map.entry("texture3DProj", "textureProj"),
		Map.entry("texture2DGrad", "textureGrad"),
		Map.entry("texture2DGradARB", "textureGrad"),
		Map.entry("texture3DGrad", "textureGrad"),
		Map.entry("texelFetch2D", "texelFetch"),
		Map.entry("texelFetch3D", "texelFetch"),
		Map.entry("textureSize2D", "textureSize")
	);

	public static void transform(Transformer root, Parameters parameters, boolean core) {
		root.rename("gl_FogFragCoord", "iris_FogFragCoord");
		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("out float iris_FogFragCoord;");
			root.prependMain("iris_FogFragCoord = 0.0f;");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			root.injectVariable("in float iris_FogFragCoord;");
		}

		if (parameters.type == ShaderType.VERTEX) {
			root.injectVariable("vec4 iris_FrontColor;");
			root.rename("gl_FrontColor", "iris_FrontColor");
		}

		if (parameters.type == ShaderType.FRAGMENT) {
			if (root.containsCall("gl_FragColor")) {
				root.replaceExpression("gl_FragColor", "gl_FragData[0]");
			}

			// Note: In compatibility profile, gl_FragData is a built-in array.
			if (!core && parameters.getAlphaTest() != AlphaTest.ALWAYS) {
				root.injectVariable("uniform float iris_currentAlphaTest;");
				root.appendMain(parameters.getAlphaTest().toExpression("gl_FragData[0].a", "iris_currentAlphaTest", ""));
			}
		}

		if (root.containsCall("texture") && root.hasVariable("texture")) {
			root.rename("texture", "gtexture");
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

		root.renameFunctionCall(COMMON_TEXTURE_RENAMES);
		root.renameAndWrapShadow("shadow2D", "texture");
		root.renameAndWrapShadow("shadow2DLod", "textureLod");
	}
}
