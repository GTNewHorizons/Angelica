package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import lombok.Getter;

public class BlendModeStorage {
	private static final int MAX_DRAW_BUFFERS = 8;

	private static boolean originalBlendEnable;
	private static final BlendState originalBlend = new BlendState();
	@Getter private static boolean blendLocked;
	@Getter private static boolean hasDeferredChanges;
	private static final int[] bufferOverrideIndices = new int[MAX_DRAW_BUFFERS];
	private static final boolean[] bufferOverrideIsDisable = new boolean[MAX_DRAW_BUFFERS];
	private static final BlendState[] bufferOverrideStates = new BlendState[MAX_DRAW_BUFFERS];
	private static int bufferOverrideCount;

	static {
		for (int i = 0; i < MAX_DRAW_BUFFERS; i++) {
			bufferOverrideStates[i] = new BlendState();
		}
	}

    public static void overrideBlend(BlendState override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			originalBlendEnable = GLStateManager.getBlendMode().isEnabled();
            originalBlend.set(GLStateManager.getBlendState());
		}

		bufferOverrideCount = 0;
		hasDeferredChanges = false;
		blendLocked = false;

		if (override == null) {
            GLStateManager.disableBlend();
		} else {
            GLStateManager.enableBlend();
            GLStateManager.tryBlendFuncSeparate(override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
		}

		blendLocked = true;
	}

	public static void overrideBufferBlend(int index, BlendState override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
            originalBlendEnable = GLStateManager.getBlendMode().isEnabled();
            originalBlend.set(GLStateManager.getBlendState());
		}

		if (override == null) {
			RenderSystem.disableBufferBlend(index);
		} else {
			RenderSystem.enableBufferBlend(index);
			RenderSystem.blendFuncSeparatei(index, override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
		}

		if (bufferOverrideCount < MAX_DRAW_BUFFERS) {
			final int slot = bufferOverrideCount++;
			bufferOverrideIndices[slot] = index;
			bufferOverrideIsDisable[slot] = (override == null);
			if (override != null) {
				bufferOverrideStates[slot].setAll(override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
			}
		}
		blendLocked = true;
	}

	public static void deferBlendModeToggle(boolean enabled) {
		originalBlendEnable = enabled;
		hasDeferredChanges = true;
	}

	public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        originalBlend.setAll(srcRgb, dstRgb, srcAlpha, dstAlpha);
		hasDeferredChanges = true;
	}

	public static void flushDeferredBlend() {
		if (!blendLocked || !hasDeferredChanges) return;
		hasDeferredChanges = false;
		blendLocked = false;

		if (originalBlendEnable) {
			GLStateManager.enableBlend();
		} else {
			GLStateManager.disableBlend();
		}
		GLStateManager.tryBlendFuncSeparate(
			originalBlend.getSrcRgb(), originalBlend.getDstRgb(),
			originalBlend.getSrcAlpha(), originalBlend.getDstAlpha());

		blendLocked = true;

		for (int i = 0; i < bufferOverrideCount; i++) {
			final int idx = bufferOverrideIndices[i];
			if (bufferOverrideIsDisable[i]) {
				RenderSystem.disableBufferBlend(idx);
			} else {
				final BlendState s = bufferOverrideStates[i];
				RenderSystem.enableBufferBlend(idx);
				RenderSystem.blendFuncSeparatei(idx, s.getSrcRgb(), s.getDstRgb(), s.getSrcAlpha(), s.getDstAlpha());
			}
		}
	}

	public static void restoreBlend() {
		if (!blendLocked) {
			return;
		}

		blendLocked = false;
		hasDeferredChanges = false;
		bufferOverrideCount = 0;

		if (originalBlendEnable) {
            GLStateManager.enableBlend();
		} else {
            GLStateManager.disableBlend();
		}

        GLStateManager.tryBlendFuncSeparate(originalBlend.getSrcRgb(), originalBlend.getDstRgb(), originalBlend.getSrcAlpha(), originalBlend.getDstAlpha());
	}
}
