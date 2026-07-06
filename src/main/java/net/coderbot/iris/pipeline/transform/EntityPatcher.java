package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.pipeline.transform.parameter.AttributeParameters;
import org.taumc.glsl.Transformer;

class EntityPatcher {

	public static void patchEntityId(Transformer transformer, AttributeParameters parameters) {
		injectUniformIfUndeclared(transformer, "entityId", "uniform int entityId;");
		injectUniformIfUndeclared(transformer, "blockEntityId", "uniform int blockEntityId;");
		injectUniformIfUndeclared(transformer, "currentRenderedItemId", "uniform int currentRenderedItemId;");
	}

	public static void patchOverlayColor(Transformer transformer, AttributeParameters parameters) {
		injectUniformIfUndeclared(transformer, "entityColor", "uniform vec4 entityColor;");
	}

	private static void injectUniformIfUndeclared(Transformer transformer, String name, String declaration) {
		if (transformer.containsCall(name) && !transformer.hasVariable(name)) {
			transformer.injectVariable(declaration);
		}
	}
}
