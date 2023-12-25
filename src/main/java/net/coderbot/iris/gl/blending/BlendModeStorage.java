package net.coderbot.iris.gl.blending;

import net.coderbot.iris.gl.IrisRenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public class BlendModeStorage {
	private static boolean originalBlendEnable;
	private static BlendMode originalBlend;
	private static boolean blendLocked;

	public static boolean isBlendLocked() {
		return blendLocked;
	}

	public static void overrideBlend(BlendMode override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			originalBlendEnable = GL11.glGetBoolean(GL11.GL_BLEND);
            originalBlend = new BlendMode(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
		}

		blendLocked = false;

		if (override == null) {
            GL11.glDisable(GL11.GL_BLEND);
		} else {
            GL11.glEnable(GL11.GL_BLEND);

            GL14.glBlendFuncSeparate(override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
		}

		blendLocked = true;
	}

	public static void overrideBufferBlend(int index, BlendMode override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
            originalBlendEnable = GL11.glGetBoolean(GL11.GL_BLEND);
            originalBlend = new BlendMode(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
		}

		if (override == null) {
			IrisRenderSystem.disableBufferBlend(index);
		} else {
			IrisRenderSystem.enableBufferBlend(index);
			IrisRenderSystem.blendFuncSeparatei(index, override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
		}

		blendLocked = true;
	}

	public static void deferBlendModeToggle(boolean enabled) {
		originalBlendEnable = enabled;
	}

	public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
		originalBlend = new BlendMode(srcRgb, dstRgb, srcAlpha, dstAlpha);
	}

	public static void restoreBlend() {
		if (!blendLocked) {
			return;
		}

		blendLocked = false;

		if (originalBlendEnable) {
            GL11.glEnable(GL11.GL_BLEND);
		} else {
            GL11.glDisable(GL11.GL_BLEND);
		}

        GL14.glBlendFuncSeparate(originalBlend.getSrcRgb(), originalBlend.getDstRgb(), originalBlend.getSrcAlpha(), originalBlend.getDstAlpha());
	}
}
