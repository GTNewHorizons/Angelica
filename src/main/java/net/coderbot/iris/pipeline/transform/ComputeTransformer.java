package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

public class ComputeTransformer {

	public static void transform(Transformer transformer, Parameters parameters) {
		CommonTransformer.transform(transformer, parameters, true);
	}
}
