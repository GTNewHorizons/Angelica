package net.coderbot.iris.shadows;

import net.coderbot.iris.pipeline.ShadowRenderer;
import org.joml.Matrix4f;

public class ShadowRenderingState {
	public static boolean areShadowsCurrentlyBeingRendered() {
		return ShadowRenderer.ACTIVE;
	}

	public static Matrix4f getShadowOrthoMatrix() {
		return ShadowRenderer.ACTIVE ? new Matrix4f(ShadowRenderer.PROJECTION) : null;
	}
}
