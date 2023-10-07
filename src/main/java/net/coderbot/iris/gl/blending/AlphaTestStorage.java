package net.coderbot.iris.gl.blending;

import org.lwjgl.opengl.GL11;

public class AlphaTestStorage {
	private static boolean originalAlphaTestEnable;
	private static AlphaTest originalAlphaTest;
	private static boolean alphaTestLocked;

	public static boolean isAlphaTestLocked() {
		return alphaTestLocked;
	}

	public static void overrideAlphaTest(AlphaTest override) {
		if (!alphaTestLocked) {
			// Only save the previous state if the alpha test wasn't already locked
			originalAlphaTestEnable = GL11.glGetBoolean(GL11.GL_ALPHA_TEST);
			originalAlphaTest = new AlphaTest(AlphaTestFunction.fromGlId(GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)).orElse(AlphaTestFunction.ALWAYS), GL11.glGetInteger(GL11.GL_ALPHA_TEST_REF));
		}

		alphaTestLocked = false;

		if (override == null) {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
		} else {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(override.getFunction().getGlId(), override.getReference());
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
            GL11.glEnable(GL11.GL_ALPHA_TEST);
		} else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
		}

        GL11.glAlphaFunc(originalAlphaTest.getFunction().getGlId(), originalAlphaTest.getReference());
	}
}
