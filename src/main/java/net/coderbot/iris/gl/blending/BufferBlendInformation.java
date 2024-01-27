package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.states.BlendState;
import lombok.Getter;

@Getter
public class BufferBlendInformation {
	private final int index;
	private final BlendState blendMode;

	public BufferBlendInformation(int index, BlendState blendMode) {
		this.index = index;
		this.blendMode = blendMode;
	}
}
