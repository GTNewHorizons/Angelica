package com.gtnewhorizons.angelica.rendering;

import me.jellysquid.mods.sodium.client.gui.FrameRateOptions;

import java.util.function.LongSupplier;

final class ReducerStateMachine {

    enum State {
        DISABLED(null),
        FORCED(" [reducer: forced]"),
        MINIMIZED(" [reducer: minimized]"),
        UNFOCUSED(" [reducer: unfocused]"),
        IDLE(" [reducer: idle]"),
        FOCUSED(null);

        private final String debugTag;

        State(String debugTag) {
            this.debugTag = debugTag;
        }

        String debugTag() {
            return debugTag;
        }
    }

    static final int MAX_FPS_SETTING = FrameRateOptions.MAX_FRAMERATE;
    static final int MINIMIZED_LOOP_HZ = 20;
    static final int MENU_CAP_HZ = 30;
    static final float MIN_VOLUME_MULT = 0.01f;

    private final LongSupplier clock;
    private State state = State.FOCUSED;
    private long lastInputNanos;
    private boolean forced;
    private boolean keybindDisabled;

    ReducerStateMachine(LongSupplier clock) {
        this.clock = clock;
        this.lastInputNanos = clock.getAsLong();
    }

    void onInput() {
        lastInputNanos = clock.getAsLong();
    }

    void toggleForced() {
        forced = !forced;
        onInput();
    }

    void toggleKeybindDisabled() {
        keybindDisabled = !keybindDisabled;
        onInput();
    }

    boolean keybindDisabled() {
        return keybindDisabled;
    }

    State state() {
        return state;
    }

    boolean evaluate(boolean enabled, boolean visible, boolean active, long idleTimeoutNanos) {
        final State next;
        if (!enabled || keybindDisabled) {
            next = State.DISABLED;
        } else if (forced) {
            next = State.FORCED;
        } else if (!visible) {
            next = State.MINIMIZED;
        } else if (!active) {
            next = State.UNFOCUSED;
        } else if (idleTimeoutNanos > 0 && clock.getAsLong() - lastInputNanos >= idleTimeoutNanos) {
            next = State.IDLE;
        } else {
            next = State.FOCUSED;
        }

        final boolean changed = next != state;
        state = next;
        return changed;
    }

    static int stateCap(State state, int unfocusedFps, int idleFps) {
        return switch (state) {
            case FORCED, UNFOCUSED -> uncapIfMax(unfocusedFps);
            case MINIMIZED -> MINIMIZED_LOOP_HZ;
            case IDLE -> uncapIfMax(idleFps);
            default -> 0;
        };
    }

    static int stateVolume(State state, int unfocusedVol, int minimizedVol, int idleVol) {
        return switch (state) {
            case FORCED, UNFOCUSED -> unfocusedVol;
            case MINIMIZED -> minimizedVol;
            case IDLE -> idleVol;
            default -> 100;
        };
    }

    static int mergeCap(int userLimit, int stateCap, boolean inMenu, boolean limitMenu) {
        int cap = uncapIfMax(userLimit);
        if (inMenu && limitMenu) {
            cap = min0(cap, MENU_CAP_HZ);
        }
        return min0(cap, stateCap);
    }

    static float masterVolume(float user, int percent) {
        return percent >= 100 ? user : user * Math.max(MIN_VOLUME_MULT, percent / 100f);
    }

    private static int uncapIfMax(int value) {
        return value >= MAX_FPS_SETTING ? 0 : value;
    }

    private static int min0(int a, int b) {
        if (a == 0) return b;
        if (b == 0) return a;
        return Math.min(a, b);
    }
}
