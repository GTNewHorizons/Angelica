package net.coderbot.iris.pipeline.transform;

import org.taumc.glsl.Util;
import org.taumc.glsl.grammar.GLSLParser;

class CompositeDepthTransformer {

    public static void transform(GLSLParser.Translation_unitContext translationUnit) {
		// replace original declaration
        int type = Util.findType(translationUnit, "centerDepthSmooth");
        if (type != 0) {
            Util.injectVariable(translationUnit, "uniform sampler2D iris_centerDepthSmooth;");
            Util.replaceExpression(translationUnit, "centerDepthSmooth", "texture2D(iris_centerDepthSmooth, vec2(0.5)).r");
        }
	}
}
