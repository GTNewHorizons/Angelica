package net.coderbot.iris.pipeline.transform;

import org.taumc.glsl.grammar.GLSLParser;

class CompositeTransformer {
	public static void transform(GLSLParser.Translation_unitContext translationUnit, int version) {
		CompositeDepthTransformer.transform(translationUnit);

        // TODO: how to do this using glsl-transformation-lib?
        // Need to specifically find that these functions are called, not 100% sure how to do that
        // Also, is it possible to inject something before declarations?
        // Modern Iris doesn't seem to have this patch, so maybe it's just not even needed
        // More likely it's just an OpenGL core thing that ensures it's present

		// if using a lod texture sampler and on version 120, patch in the extension
		// #extension GL_ARB_shader_texture_lod : require
//		if (version <= 120
//				&& Stream.concat(
//						root.identifierIndex.getStream("texture2DLod"),
//						root.identifierIndex.getStream("texture3DLod"))
//						.filter(id -> id.getParent() instanceof FunctionCallExpression)
//						.findAny().isPresent()) {
//			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
//					"#extension GL_ARB_shader_texture_lod : require\n")
	}
}
