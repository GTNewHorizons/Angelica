package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.states.DepthState;
import com.gtnewhorizons.angelica.glsm.states.GLColorMask;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;

public class DepthColorStorage {
	private static boolean originalDepthEnable;
	private static ColorMask originalColor;
	@Getter
    private static boolean depthColorLocked;

    public static void disableDepthColor() {
		if (!depthColorLocked) {
			// Only save the previous state if the depth and color mask wasn't already locked
			GLColorMask colorMask = GLStateManager.getColorMask();
			final DepthState depthState = GLStateManager.getDepth();

			originalDepthEnable = depthState.mask;
			originalColor = new ColorMask(colorMask.red, colorMask.green, colorMask.blue, colorMask.alpha);
		}

		depthColorLocked = false;

		GLStateManager.glDepthMask(false);
        GLStateManager.glColorMask(false, false, false, false);

		depthColorLocked = true;
	}

	public static void deferDepthEnable(boolean enabled) {
		originalDepthEnable = enabled;
	}

	public static void deferColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		originalColor = new ColorMask(red, green, blue, alpha);
	}

	public static void unlockDepthColor() {
		if (!depthColorLocked) {
			return;
		}

		depthColorLocked = false;

        GLStateManager.glDepthMask(originalDepthEnable);

        GLStateManager.glColorMask(originalColor.isRedMasked(), originalColor.isGreenMasked(), originalColor.isBlueMasked(), originalColor.isAlphaMasked());
	}
}
