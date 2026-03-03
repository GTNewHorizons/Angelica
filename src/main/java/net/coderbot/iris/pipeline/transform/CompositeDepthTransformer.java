package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

class CompositeDepthTransformer {

	public static void transform(Transformer transformer, Parameters parameters, int glslVersion) {
		CommonTransformer.transform(transformer, parameters, true, glslVersion);

		CoreTransformHelper.injectMatrixUniforms(transformer);

		if (parameters.type == ShaderType.VERTEX) {
			CoreTransformHelper.injectCompositeVertexAttributes(transformer);
		}

		final int type = transformer.findType("centerDepthSmooth");
		if (type != 0) {
			transformer.injectVariable("uniform sampler2D iris_centerDepthSmooth;");
			transformer.replaceExpression("centerDepthSmooth", "texture2D(iris_centerDepthSmooth, vec2(0.5)).r");
		}
	}
}
