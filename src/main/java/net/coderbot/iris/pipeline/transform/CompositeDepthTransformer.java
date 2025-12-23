package net.coderbot.iris.pipeline.transform;

import org.taumc.glsl.Transformer;

class CompositeDepthTransformer {

    public static void transform(Transformer transformer) {
		// replace original declaration
        int type = transformer.findType("centerDepthSmooth");
        if (type != 0) {
            transformer.injectVariable("uniform sampler2D iris_centerDepthSmooth;");
            transformer.replaceExpression("centerDepthSmooth", "texture2D(iris_centerDepthSmooth, vec2(0.5)).r");
        }
	}
}
