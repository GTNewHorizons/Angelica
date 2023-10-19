package net.coderbot.iris.gl.blending;

import org.lwjgl.opengl.GL11;

public class DepthColorStorage {
	private static boolean originalDepthEnable;
	private static ColorMask originalColor;
	private static boolean depthColorLocked;

	public static boolean isDepthColorLocked() {
		return depthColorLocked;
	}

	public static void disableDepthColor() {
        throw new RuntimeException("Iris disabled depth color");
//		if (!depthColorLocked) {
//			// Only save the previous state if the depth and color mask wasn't already locked
//			GlStateManager.ColorMask colorMask = GlStateManagerAccessor.getCOLOR_MASK();
//			GlStateManager.DepthState depthState = GlStateManagerAccessor.getDEPTH();
//
//			originalDepthEnable = depthState.mask;
//			originalColor = new ColorMask(colorMask.red, colorMask.green, colorMask.blue, colorMask.alpha);
//		}
//
//		depthColorLocked = false;
//
//		GL11.glDepthMask(false);
//		GL11.glColorMask(false, false, false, false);
//
//		depthColorLocked = true;
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

		if (originalDepthEnable) {
            GL11.glDepthMask(true);
		} else {
            GL11.glDepthMask(false);
		}

        GL11.glColorMask(originalColor.isRedMasked(), originalColor.isGreenMasked(), originalColor.isBlueMasked(), originalColor.isAlphaMasked());
	}
}
