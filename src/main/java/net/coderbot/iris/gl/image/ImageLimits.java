package net.coderbot.iris.gl.image;

import com.gtnewhorizons.angelica.glsm.RenderSystem;

public class ImageLimits {
	private final int maxImageUnits;
	private static ImageLimits instance;

	private ImageLimits() {
		this.maxImageUnits = RenderSystem.getMaxImageUnits();
	}

	public int getMaxImageUnits() {
		return maxImageUnits;
	}

	public static ImageLimits get() {
		if (instance == null) {
			instance = new ImageLimits();
		}

		return instance;
	}
}
