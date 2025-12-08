package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public class BlendModeStorage {
	private static boolean originalBlendEnable;
	private static final BlendState originalBlend = new BlendState();
	@Getter private static boolean blendLocked;

    public static void overrideBlend(BlendState override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			originalBlendEnable = GL11.glGetBoolean(GL11.GL_BLEND);
            originalBlend.set(GLStateManager.getBlendState());
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

	public static void overrideBufferBlend(int index, BlendState override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
            originalBlendEnable = GL11.glGetBoolean(GL11.GL_BLEND);
            originalBlend.set(GLStateManager.getBlendState());
		}

		if (override == null) {
			RenderSystem.disableBufferBlend(index);
		} else {
			RenderSystem.enableBufferBlend(index);
			RenderSystem.blendFuncSeparatei(index, override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
		}

		blendLocked = true;
	}

	public static void deferBlendModeToggle(boolean enabled) {
		originalBlendEnable = enabled;
	}

	public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        originalBlend.setAll(srcRgb, dstRgb, srcAlpha, dstAlpha);
	}

	public static void restoreBlend() {
		if (!blendLocked) {
			return;
		}

		blendLocked = false;

		if (originalBlendEnable) {
            GLStateManager.enableBlend();
		} else {
            GLStateManager.disableBlend();
		}

        GLStateManager.tryBlendFuncSeparate(originalBlend.getSrcRgb(), originalBlend.getDstRgb(), originalBlend.getSrcAlpha(), originalBlend.getDstAlpha());
	}
}
