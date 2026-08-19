package net.coderbot.iris.gl.blending;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaBooleanLayer;
import com.gtnewhorizons.angelica.glsm.hooks.VanillaStateLayer;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import lombok.Getter;

public class BlendModeStorage {
    private static final int MAX_DRAW_BUFFERS = 8;

    private static boolean vanillaBlendEnable;
    private static final BlendState vanillaBlend = new BlendState();

    @Getter
    private static boolean blendLocked;
    @Getter
    private static boolean overrideHeld;

    private static boolean hasGlobalOverride;

    private static boolean hasDeferredChanges;
    private static final int[] bufferOverrideIndices = new int[MAX_DRAW_BUFFERS];
    private static final boolean[] bufferOverrideIsDisable = new boolean[MAX_DRAW_BUFFERS];
    private static final BlendState[] bufferOverrideStates = new BlendState[MAX_DRAW_BUFFERS];
    private static int bufferOverrideCount;
    private static boolean bufferOverridesDirty;

    static {
        for (int i = 0; i < MAX_DRAW_BUFFERS; i++) {
            bufferOverrideStates[i] = new BlendState();
        }
    }

    public static void overrideBlend(BlendState override) {
        captureVanilla();

        bufferOverrideCount = 0;
        bufferOverridesDirty = false;
        hasDeferredChanges = false;
        overrideHeld = true;
        hasGlobalOverride = true;

        blendLocked = false;
        try {
            if (override == null) {
                GLStateManager.disableBlend();
            } else {
                GLStateManager.enableBlend();
                GLStateManager.tryBlendFuncSeparate(override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
            }
        } finally {
            blendLocked = true;
        }
    }

    public static void overrideBufferBlend(int index, BlendState override) {
        captureVanilla();

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
        overrideHeld = true;
        blendLocked = true;
    }

    private static void captureVanilla() {
        if (!overrideHeld) {
            vanillaBlendEnable = GLStateManager.getBlendMode().isEnabled();
            vanillaBlend.set(GLStateManager.getBlendState());
        }
    }

    public static void deferBlendModeToggle(boolean enabled) {
        vanillaBlendEnable = enabled;
        hasDeferredChanges = true;
    }

    public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        vanillaBlend.setAll(srcRgb, dstRgb, srcAlpha, dstAlpha);
        hasDeferredChanges = true;
    }

    public static void flushDeferredBlend() {
        if (!overrideHeld) return;

        if (hasDeferredChanges && !hasGlobalOverride) {
            hasDeferredChanges = false;
            applyVanilla();
            bufferOverridesDirty = true;
        }

        if (bufferOverridesDirty) {
            bufferOverridesDirty = false;
            reassertBufferOverrides();
        }
    }

    private static void reassertBufferOverrides() {
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
        if (!overrideHeld) {
            return;
        }

        hasGlobalOverride = false;
        hasDeferredChanges = false;
        bufferOverrideCount = 0;
        bufferOverridesDirty = false;
        blendLocked = false;

        applyVanilla();
        overrideHeld = false;
    }

    private static void applyVanilla() {
        final boolean wasLocked = blendLocked;
        blendLocked = false;
        try {
            if (vanillaBlendEnable) {
                GLStateManager.enableBlend();
            } else {
                GLStateManager.disableBlend();
            }
            GLStateManager.tryBlendFuncSeparate(vanillaBlend.getSrcRgb(), vanillaBlend.getDstRgb(), vanillaBlend.getSrcAlpha(), vanillaBlend.getDstAlpha());
        } finally {
            blendLocked = wasLocked;
        }
    }

    public static final VanillaBooleanLayer ENABLE_LAYER = new VanillaBooleanLayer() {
        @Override
        public boolean isOverrideHeld() {
            return overrideHeld;
        }

        @Override
        public boolean getVanilla() {
            return vanillaBlendEnable;
        }

        @Override
        public void setVanilla(boolean enabled) {
            deferBlendModeToggle(enabled);
        }
    };

    public static final VanillaStateLayer<BlendState> FUNC_LAYER = new VanillaStateLayer<>() {
        @Override
        public boolean isOverrideHeld() {
            return overrideHeld;
        }

        @Override
        public void readVanilla(BlendState into) {
            into.setAll(vanillaBlend.getSrcRgb(), vanillaBlend.getDstRgb(), vanillaBlend.getSrcAlpha(), vanillaBlend.getDstAlpha());
        }

        @Override
        public void writeVanilla(BlendState from) {
            deferBlendFunc(from.getSrcRgb(), from.getDstRgb(), from.getSrcAlpha(), from.getDstAlpha());
        }
    };
}
