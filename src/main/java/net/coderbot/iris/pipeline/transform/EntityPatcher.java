package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLParser;

class EntityPatcher {

	public static void patchEntityId(Transformer transformer, AttributeParameters parameters) {
		if (!hasInstanceAttributes(parameters)) {
			injectUniformIfUndeclared(transformer, "entityId", "uniform int entityId;");
			injectUniformIfUndeclared(transformer, "blockEntityId", "uniform int blockEntityId;");
			injectUniformIfUndeclared(transformer, "currentRenderedItemId", "uniform int currentRenderedItemId;");
			return;
		}

		removeIfDeclared(transformer, "entityId");
		removeIfDeclared(transformer, "blockEntityId");
		removeIfDeclared(transformer, "currentRenderedItemId");

		final String geometryIn = parameters.hasTesselation ? "iris_entityInfoTES" : "iris_entityInfo";
		final String info = switch (parameters.type) {
			case GEOMETRY -> geometryIn + "[0]";
			case TESSELATION_CONTROL -> "iris_entityInfo[gl_InvocationID]";
			case TESSELATION_EVAL -> "iris_entityInfoTCS[0]";
			default -> "iris_entityInfo";
		};
		transformer.replaceExpression("entityId", info + ".x", GLSLParser::postfix_expression);
		transformer.replaceExpression("blockEntityId", info + ".y", GLSLParser::postfix_expression);
		transformer.replaceExpression("currentRenderedItemId", info + ".z", GLSLParser::postfix_expression);

		switch (parameters.type) {
			case VERTEX -> {
				if (!transformer.hasVariable("iris_Entity")) {
					transformer.injectVariable("layout(location = " + InstancedAttribs.LOC_ENTITY + ") in ivec4 iris_Entity;");
				}
				transformer.injectVariable("flat out ivec3 iris_entityInfo;");
				transformer.prependMain("iris_entityInfo = iris_Entity.xyz;");
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
