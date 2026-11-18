package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.shader.ShaderIndex;
import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLParser;

class EntityPatcher {

	private static final String[] ID_NAMES = { "entityId", "blockEntityId", "currentRenderedItemId" };
	private static final String[] ID_COMPONENTS = { ".x", ".y", ".z" };

	public static void patchEntityId(Transformer transformer, AttributeParameters parameters) {
		final String geometryIn = parameters.hasTesselation ? "iris_entityInfoTES" : "iris_entityInfo";
		final String info = switch (parameters.type) {
			case GEOMETRY -> geometryIn + "[0]";
			case TESSELATION_CONTROL -> "iris_entityInfo[gl_InvocationID]";
			case TESSELATION_EVAL -> "iris_entityInfoTCS[0]";
			default -> "iris_entityInfo";
		};
		final StringBuilder assignments = new StringBuilder();
		for (int i = 0; i < ID_NAMES.length; i++) {
			final String name = ID_NAMES[i];
			if (!transformer.containsCall(name)) continue;
			final GLSLParser.Typeless_declarationContext global = topLevelDeclarator(transformer, name);
			if (global != null) {
				if (!isUniform(global)) continue;
				// removeVariable takes the last declaration it finds by name, which can be a pack local
				final String removed = "iris_removed_" + name;
				((CommonToken) global.IDENTIFIER().getSymbol()).setText(removed);
				transformer.removeVariable(removed);
			}
			transformer.injectVariable("int " + name + ";");
			assignments.append(name).append(" = ").append(info).append(ID_COMPONENTS[i]).append("; ");
		}
		if (parameters.type != ShaderType.VERTEX && assignments.length() > 0) {
			transformer.prependMain("{ " + assignments + "}");
		}

		switch (parameters.type) {
			case VERTEX -> {
				final boolean instanced = hasInstanceAttributes(parameters);
				final String input = instanced ? "iris_InstEntity" : "iris_Entity";
				final int location = instanced ? InstancedAttribs.LOC_ENTITY : ImmediateExtendedAttribHandler.LOC_ENTITY_IDS;
				removeIfDeclared(transformer, "iris_Entity");
				removeIfDeclared(transformer, "iris_InstEntity");
				transformer.injectVariable("layout(location = " + location + ") in vec4 " + input + ";");
				transformer.injectVariable("flat out ivec3 iris_entityInfo;");
				transformer.prependMain("{ ivec3 iris_ids = ivec3(round(" + input + ".xyz * 65535.0)); "
					+ "iris_entityInfo = ivec3(iris_ids.x == 65535 ? -1 : iris_ids.x, iris_ids.y == 65535 ? -1 : iris_ids.y, "
					+ "iris_ids.z == 65535 ? -1 : iris_ids.z); " + assignments + "}");
			}
			case TESSELATION_CONTROL -> {
				transformer.injectVariable("flat out ivec3 iris_entityInfoTCS[];");
				transformer.injectVariable("flat in ivec3 iris_entityInfo[];");
				transformer.prependMain("iris_entityInfoTCS[gl_InvocationID] = iris_entityInfo[gl_InvocationID];");
			}
			case TESSELATION_EVAL -> {
				transformer.injectVariable("flat out ivec3 iris_entityInfoTES;");
				transformer.injectVariable("flat in ivec3 iris_entityInfoTCS[];");
				transformer.prependMain("iris_entityInfoTES = iris_entityInfoTCS[0];");
			}
			case GEOMETRY -> {
				transformer.injectVariable("flat out ivec3 iris_entityInfoGS;");
				transformer.injectVariable("flat in ivec3 " + geometryIn + "[];");
				transformer.prependMain("iris_entityInfoGS = " + geometryIn + "[0];");
			}
			case FRAGMENT -> {
				if (transformer.containsCall("iris_entityInfo")) {
					transformer.injectVariable("flat in ivec3 iris_entityInfo;");
					if (parameters.hasGeometry) {
						transformer.rename("iris_entityInfo", "iris_entityInfoGS");
					} else if (parameters.hasTesselation) {
						transformer.rename("iris_entityInfo", "iris_entityInfoTES");
					}
				}
			}
			default -> {}
		}
	}

	public static void patchOverlayColor(Transformer transformer, AttributeParameters parameters) {
		if (!hasInstanceAttributes(parameters)) {
			injectUniformIfUndeclared(transformer, "entityColor", "uniform vec4 entityColor;");
			return;
		}

		removeIfDeclared(transformer, "entityColor");

		switch (parameters.type) {
			case VERTEX -> {
				transformer.injectVariable("out vec4 entityColor;");
				transformer.prependMain("{ entityColor = iris_InstOverlay; entityColor.rgb *= float(entityColor.a != 0.0); }");
			}
			case TESSELATION_CONTROL -> {
				transformer.replaceExpression("entityColor", "entityColor[gl_InvocationID]", GLSLParser::postfix_expression);
				transformer.injectVariable("patch out vec4 entityColorTCS;");
				transformer.injectVariable("in vec4 entityColor[];");
				transformer.prependMain("entityColorTCS = entityColor[gl_InvocationID];");
			}
			case TESSELATION_EVAL -> {
				transformer.replaceExpression("entityColor", "entityColorTCS", GLSLParser::postfix_expression);
				transformer.injectVariable("out vec4 entityColorTES;");
				transformer.injectVariable("patch in vec4 entityColorTCS;");
				transformer.prependMain("entityColorTES = entityColorTCS;");
			}
			case GEOMETRY -> {
				final String geometryIn = parameters.hasTesselation ? "entityColorTES" : "entityColor";
				transformer.replaceExpression("entityColor", geometryIn + "[0]", GLSLParser::postfix_expression);
				transformer.injectVariable("out vec4 entityColorGS;");
				transformer.injectVariable("in vec4 " + geometryIn + "[];");
				transformer.prependMain("entityColorGS = " + geometryIn + "[0];");
			}
			case FRAGMENT -> {
				if (transformer.containsCall("entityColor")) {
					transformer.injectVariable("in vec4 entityColor;");
					if (parameters.hasGeometry) {
						transformer.rename("entityColor", "entityColorGS");
					} else if (parameters.hasTesselation) {
						transformer.rename("entityColor", "entityColorTES");
					}
				}
			}
			default -> {}
		}
	}

	private static GLSLParser.Typeless_declarationContext topLevelDeclarator(Transformer transformer, String name) {
		final GLSLParser.Typeless_declarationContext[] found = new GLSLParser.Typeless_declarationContext[1];
		transformer.mutateTree(root -> {
			for (GLSLParser.External_declarationContext external : root.external_declaration()) {
				final GLSLParser.DeclarationContext declaration = external.declaration();
				if (declaration == null || declaration.init_declarator_list() == null) continue;
				for (GLSLParser.Typeless_declarationContext declarator : ShaderIndex.declarators(declaration.init_declarator_list())) {
					if (declarator.IDENTIFIER().getText().equals(name)) found[0] = declarator;
				}
			}
		});
		return found[0];
	}

	private static boolean isUniform(GLSLParser.Typeless_declarationContext declarator) {
		final ParserRuleContext parent = declarator.getParent();
		final GLSLParser.Init_declarator_listContext list = (GLSLParser.Init_declarator_listContext)
			(parent instanceof GLSLParser.Single_declarationContext ? parent.getParent() : parent);
		return ShaderIndex.hasStorageQualifier(list.single_declaration().fully_specified_type().type_qualifier(), "uniform");
	}

	private static boolean hasInstanceAttributes(AttributeParameters parameters) {
		return parameters.instancing.hasInstanceHead();
	}

	private static void injectUniformIfUndeclared(Transformer transformer, String name, String declaration) {
		if (transformer.containsCall(name) && !transformer.hasVariable(name)) {
			transformer.injectVariable(declaration);
		}
	}

	private static void removeIfDeclared(Transformer transformer, String name) {
		if (transformer.hasVariable(name)) {
			transformer.removeVariable(name);
		}
	}
}
