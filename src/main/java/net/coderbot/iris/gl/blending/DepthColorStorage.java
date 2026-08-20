package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.states.DepthState;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import lombok.Getter;

public class DepthColorStorage {
    private static boolean vanillaDepthEnable;
    private static final ColorMask vanillaColor = new ColorMask();

    @Getter
    private static boolean depthColorLocked;

    @Getter
    private static boolean overrideHeld;

    private static final IntOpenHashSet ownedPrograms = new IntOpenHashSet();

    public static void registerOwnedProgram(int programId) {
        ownedPrograms.add(programId);
    }

    public static void unregisterOwnedProgram(int programId) {
        ownedPrograms.remove(programId);
    }

    public static boolean isOwnedProgram(int programId) {
        return ownedPrograms.contains(programId);
    }

    public static void disableDepthColor() {
        if (!overrideHeld) {
            final ColorMask colorMask = GLStateManager.getColorMask();
            final DepthState depthState = GLStateManager.getDepthState();

            vanillaDepthEnable = depthState.isEnabled();
            vanillaColor.set(colorMask);
        }

        overrideHeld = true;

        depthColorLocked = false;
        try {
            GLStateManager.glDepthMask(false);
            GLStateManager.glColorMask(false, false, false, false);
        } finally {
            depthColorLocked = true;
        }
    }

    public static void deferDepthEnable(boolean enabled) {
        vanillaDepthEnable = enabled;
    }

    public static void deferColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        vanillaColor.setAll(red, green, blue, alpha);
    }

    public static void unlockDepthColor() {
        if (!overrideHeld) {
            return;
        }

        depthColorLocked = false;

        GLStateManager.glDepthMask(vanillaDepthEnable);
        GLStateManager.glColorMask(vanillaColor.red, vanillaColor.green, vanillaColor.blue, vanillaColor.alpha);
        overrideHeld = false;
    }

    public static final VanillaStateLayer<DepthState> DEPTH_LAYER = new VanillaStateLayer<>() {
        @Override
        public boolean isOverrideHeld() {
            return overrideHeld;
        }

        @Override
        public void readVanilla(DepthState into) {
            into.setEnabled(vanillaDepthEnable);
        }

        @Override
        public void writeVanilla(DepthState from) {
            vanillaDepthEnable = from.isEnabled();
        }
    };

    public static final VanillaStateLayer<ColorMask> COLOR_LAYER = new VanillaStateLayer<>() {
        @Override
        public boolean isOverrideHeld() {
            return overrideHeld;
        }

        @Override
        public void readVanilla(ColorMask into) {
            into.set(vanillaColor);
        }

        @Override
        public void writeVanilla(ColorMask from) {
            vanillaColor.set(from);
        }
    };
}
