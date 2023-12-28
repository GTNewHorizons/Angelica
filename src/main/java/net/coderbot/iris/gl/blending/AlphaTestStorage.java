package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import lombok.Getter;

public class AlphaTestStorage {
	private static boolean originalAlphaTestEnable;
	private static AlphaTest originalAlphaTest;
	@Getter
    private static boolean alphaTestLocked;

    public static void overrideAlphaTest(AlphaTest override) {
		if (!alphaTestLocked) {
            final AlphaState alphaState = GLStateManager.getAlphaState();

			// Only save the previous state if the alpha test wasn't already locked
			originalAlphaTestEnable = GLStateManager.getAlphaTest().isEnabled();
			originalAlphaTest = new AlphaTest(AlphaTestFunction.fromGlId(alphaState.getFunction()).orElse(AlphaTestFunction.ALWAYS), alphaState.getReference());
		}

		alphaTestLocked = false;

		if (override == null) {
            GLStateManager.disableAlphaTest();
		} else {
            GLStateManager.enableAlphaTest();
            GLStateManager.glAlphaFunc(override.getFunction().getGlId(), override.getReference());
		}

		alphaTestLocked = true;
	}

	public static void deferAlphaTestToggle(boolean enabled) {
		originalAlphaTestEnable = enabled;
	}

	public static void deferAlphaFunc(int function, float reference) {
		originalAlphaTest = new AlphaTest(AlphaTestFunction.fromGlId(function).get(), reference);
	}

	public static void restoreAlphaTest() {
		if (!alphaTestLocked) {
			return;
		}

		alphaTestLocked = false;

		if (originalAlphaTestEnable) {
            GLStateManager.enableAlphaTest();
		} else {
            GLStateManager.disableAlphaTest();
		}

        GLStateManager.glAlphaFunc(originalAlphaTest.getFunction().getGlId(), originalAlphaTest.getReference());
	}
}
