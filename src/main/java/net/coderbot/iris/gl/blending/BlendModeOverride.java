package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.states.BlendState;

public class BlendModeOverride {
	public static final BlendModeOverride OFF = new BlendModeOverride(null);

	private final BlendState blendMode;

	public BlendModeOverride(BlendState blendMode) {
		this.blendMode = blendMode;
	}

	public void apply() {
		BlendModeStorage.overrideBlend(this.blendMode);
	}

	public static void restore() {
		BlendModeStorage.restoreBlend();
	}
}
