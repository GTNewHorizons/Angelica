package net.coderbot.iris.pipeline.transform;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.Profile;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.basic.ASTNode;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.match.AutoHintedMatcher;
import io.github.douira.glsl_transformer.ast.query.match.Matcher;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import net.coderbot.iris.gl.shader.ShaderType;

import java.util.stream.Stream;

/**
 * Implements AttributeShaderTransformer using glsl-transformer AST
 * transformation methods.
 */
class AttributeTransformer {
	public static void transform(
			ASTParser t,
			TranslationUnit tree,
			Root root,
			AttributeParameters parameters) {
		boolean isCore = (tree.getVersionStatement().profile == Profile.CORE || (tree.getVersionStatement().profile == null && tree.getVersionStatement().version.number > 140));
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
			root.rename("gl_MultiTexCoord1", "gl_MultiTexCoord2");
		}

		Stream<Identifier> stream = Stream.empty();
		boolean hasItems = false;
		if (!parameters.inputs.lightmap) {
			stream = Stream.concat(stream,
					root.identifierIndex.getStream("gl_MultiTexCoord1"));
			stream = Stream.concat(stream,
					root.identifierIndex.getStream("gl_MultiTexCoord2"));
			hasItems = true;
		}
		if (!parameters.inputs.texture) {
			stream = Stream.concat(stream,
					root.identifierIndex.getStream("gl_MultiTexCoord0"));
			hasItems = true;
		}
		if (hasItems) {
			root.replaceReferenceExpressions(t, stream, "vec4(240.0, 240.0, 0.0, 1.0)");
		}

		patchTextureMatrices(t, tree, root, parameters.inputs.lightmap);

		if (parameters.type.glShaderType == ShaderType.VERTEX
				&& root.identifierIndex.has("gl_MultiTexCoord3")
				&& !root.identifierIndex.has("mc_midTexCoord")) {
			// TODO: proper type conversion
			// gl_MultiTexCoord3 is a super legacy alias of mc_midTexCoord. We don't do this
			// replacement if we think mc_midTexCoord could be defined just we can't handle
			// an existing declaration robustly. But basically the proper way to do this is
			// to define mc_midTexCoord only if it's not defined, and if it is defined,
			// figure out its type, then replace all occurrences of gl_MultiTexCoord3 with
			// the correct conversion from mc_midTexCoord's declared type to vec4.
			root.rename("gl_MultiTexCoord3", "mc_midTexCoord");
			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
					"attribute vec4 mc_midTexCoord;");
		}
	}

	private static void patchTextureMatrices(
			ASTParser t,
			TranslationUnit tree,
			Root root,
			boolean hasLightmap) {
		root.rename("gl_TextureMatrix", "iris_TextureMatrix");

		tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
				"const float iris_ONE_OVER_256 = 0.00390625;",
				"const float iris_ONE_OVER_32 = iris_ONE_OVER_256 * 8;");
		if (hasLightmap) {
			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
					"mat4 iris_LightmapTextureMatrix = gl_TextureMatrix[2];");
		} else {
			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_FUNCTIONS, "mat4 iris_LightmapTextureMatrix =" +
					"mat4(iris_ONE_OVER_256, 0.0, 0.0, 0.0," +
					"     0.0, iris_ONE_OVER_256, 0.0, 0.0," +
					"     0.0, 0.0, iris_ONE_OVER_256, 0.0," +
					"     iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_256);");
		}

		// column major
		tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_FUNCTIONS, "mat4 iris_TextureMatrix[8] = mat4[8](" +
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
