package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.states.BlendState;

public class BufferBlendOverride {
	private final int drawBuffer;
	private final BlendState blendMode;

	public BufferBlendOverride(int drawBuffer, BlendState blendMode) {
		this.drawBuffer = drawBuffer;
		this.blendMode = blendMode;
	}

	public void apply() {
		BlendModeStorage.overrideBufferBlend(this.drawBuffer, this.blendMode);
	}
}
