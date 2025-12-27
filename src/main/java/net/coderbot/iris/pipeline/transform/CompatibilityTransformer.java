package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.antlr.v4.runtime.tree.ParseTree;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.Map;

public class CompatibilityTransformer {

    private static final ShaderType[] pipeline = {ShaderType.VERTEX, ShaderType.GEOMETRY, ShaderType.FRAGMENT};


    public static void transformEach(Transformer transformer, Parameters parameters) {
        if (parameters.type == PatchShaderType.VERTEX) {
            ;
            // TODO: sildur's jankness
            // This is a hacky patch for sildur's shaders that changes the way it does it's waving water to make it work better?
            // Why is this the GLSL transformation code in Iris, tell sildur to fix it and remove this?
            // it's still there in current, modern Iris
            // See https://github.com/IrisShaders/Iris/issues/509
            transformer.replaceExpression("fract(worldpos.y + 0.001)", "fract(worldpos.y + 0.01)");
        }

        /**
         * Removes const storage qualifier from declarations in functions if they are
         * initialized with const parameters. Const parameters are immutable parameters
         * and can't be used to initialize const declarations because they expect
         * constant, not just immutable, expressions. This varies between drivers and
         * versions. Also removes the const qualifier from declarations that use the
         * identifiers from which the declaration was removed previously.
         * See https://wiki.shaderlabs.org/wiki/Compiler_Behavior_Notes
         */
        transformer.removeUnusedFunctions();
        transformer.removeConstAssignment();

        // TODO: glsl-transformation-lib doesn't have a way to identify empty declarations
        // this transformation is not done in current versions of Iris, so not sure it's necessary
//		boolean emptyDeclarationHit = root.process(
//				root.nodeIndex.getStream(EmptyDeclaration.class),
//				ASTNode::detachAndDelete);
//		if (emptyDeclarationHit) {
//			LOGGER.warn(
//					"Removed empty external declarations (\";\").");
//		}
    }

	// does transformations that require cross-shader type data
    public static void transformGrouped(Map<PatchShaderType, Transformer> trees, Parameters parameters) {
		/**
		 * find attributes that are declared as "in" in geometry or fragment but not
		 * declared as "out" in the previous stage. The missing "out" declarations for
		 * these attributes are added and initialized.
		 *
		 * It doesn't bother with array specifiers because they are only legal in
		 * geometry shaders, but then also only as an in declaration. The out
		 * declaration in the vertex shader is still just a single value. Missing out
		 * declarations in the geometry shader are also just normal.
		 *
		 * TODO:
		 * - fix issues where Iris' own declarations are detected and patched like
		 * iris_FogFragCoord if there are geometry shaders present
		 * - improved geometry shader support? They use funky declarations
		 */
        ShaderType prevType = null;
        for (ShaderType type : pipeline) {
            PatchShaderType[] patchTypes = PatchShaderType.fromGlShaderType(type);

            // check if the patch types have sources and continue if not
            boolean hasAny = false;
            for (PatchShaderType currentType : patchTypes) {
                if (trees.get(currentType) != null) {
                    hasAny = true;
                }
            }
            if (!hasAny) {
                continue;
            }

            // if the current type has sources but the previous one doesn't, set the
            // previous one and continue
            if (prevType == null) {
                prevType = type;
                continue;
            }

            PatchShaderType prevPatchTypes = PatchShaderType.fromGlShaderType(prevType)[0];
            Transformer prevTransformer = trees.get(prevPatchTypes);

            // find out declarations
            Map<String, GLSLParser.Single_declarationContext> outDec = prevTransformer.findQualifiers(GLSLLexer.OUT);
            for (PatchShaderType currentType : patchTypes) {
                Transformer currentTransformer = trees.get(currentType);

                if (currentTransformer == null) {
                    continue;
                }

                Map<String, GLSLParser.Single_declarationContext> inDec = currentTransformer.findQualifiers(GLSLLexer.IN);
                for (String in : inDec.keySet()) {
                    if (in.startsWith("gl_")) {
                        continue;
                    }

                    if (!outDec.containsKey(in)) {
                        if (!currentTransformer.containsCall(in)) {
                            continue;
                        }

                        prevTransformer.makeOutDeclaration(inDec.get(in), in);

                        if (!prevTransformer.hasAssigment(in)) {
                            prevTransformer.initialize(inDec.get(in), in);
                        }
                    } else {
                        ParseTree outType = outDec.get(in).fully_specified_type().type_specifier().type_specifier_nonarray().children.get(0);
                        ParseTree inType = inDec.get(in).fully_specified_type().type_specifier().type_specifier_nonarray().children.get(0);

                        if (outDec.get(in).fully_specified_type().type_specifier().array_specifier() != null) {
                            continue;
                        }

                        if (inType.getText().equals(outType.getText())) {
                            if (!prevTransformer.hasAssigment(in)) {
                                prevTransformer.initialize(inDec.get(in), in);
                            }
                        }
                    }
                }
            }
            prevType = type;
        }
    }
}
