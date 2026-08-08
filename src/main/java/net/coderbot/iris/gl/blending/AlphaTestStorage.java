package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaBooleanLayer;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

public class AlphaTestStorage {
    private static boolean vanillaEnabled;
    private static int vanillaFunction = GL11.GL_ALWAYS;
    private static float vanillaReference;

    @Getter
    private static boolean alphaTestLocked;

    private static boolean overrideHeld;

    public static void overrideAlphaTest(AlphaTest override) {
        if (!overrideHeld) {
            final AlphaState alphaState = GLStateManager.getAlphaState();

            // Only save the previous state if the alpha test wasn't already overridden
            vanillaEnabled = GLStateManager.getAlphaTest().isEnabled();
            vanillaFunction = alphaState.getFunction();
            vanillaReference = alphaState.getReference();
        }

        overrideHeld = true;

        if (override == null) {
            apply(false, GL11.GL_ALWAYS, 0.0f);
        } else {
            apply(true, override.getFunction().getGlId(), override.getReference());
        }
    }

    private static void apply(boolean enabled, int function, float reference) {
        alphaTestLocked = false;
        try {
            if (enabled) {
                GLStateManager.enableAlphaTest();
                GLStateManager.glAlphaFunc(function, reference);
            } else {
                GLStateManager.disableAlphaTest();
            }
        } finally {
            alphaTestLocked = true;
        }
    }

    public static void deferAlphaTestToggle(boolean enabled) {
        vanillaEnabled = enabled;
    }

    public static void deferAlphaFunc(int function, float reference) {
        vanillaFunction = function;
        vanillaReference = reference;
    }

    public static void restoreAlphaTest() {
        if (!overrideHeld) {
            return;
        }

        alphaTestLocked = false;

        if (vanillaEnabled) {
            GLStateManager.enableAlphaTest();
        } else {
            GLStateManager.disableAlphaTest();
        }

        GLStateManager.glAlphaFunc(vanillaFunction, vanillaReference);
        overrideHeld = false;
    }

    public static final VanillaBooleanLayer ENABLE_LAYER = new VanillaBooleanLayer() {
        @Override
        public boolean isOverrideHeld() {
            return overrideHeld;
        }

        @Override
        public boolean getVanilla() {
            return vanillaEnabled;
        }

        @Override
        public void setVanilla(boolean enabled) {
            vanillaEnabled = enabled;
        }
    };

    public static final VanillaStateLayer<AlphaState> FUNC_LAYER = new VanillaStateLayer<>() {
        @Override
        public boolean isOverrideHeld() {
            return overrideHeld;
        }

        @Override
        public void readVanilla(AlphaState into) {
            into.setFunction(vanillaFunction);
            into.setReference(vanillaReference);
        }

        @Override
        public void writeVanilla(AlphaState from) {
            vanillaFunction = from.getFunction();
            vanillaReference = from.getReference();
        }
    };
}
