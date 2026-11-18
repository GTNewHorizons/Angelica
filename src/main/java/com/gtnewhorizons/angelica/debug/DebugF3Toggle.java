package com.gtnewhorizons.angelica.debug;

import org.lwjgl.input.Keyboard;

public final class DebugF3Toggle {

    private boolean armed;
    private boolean comboUsed;
    private boolean shift;
    private boolean alt;

    public void arm(boolean shift, boolean alt) {
        armed = true;
        comboUsed = false;
        this.shift = shift;
        this.alt = alt;
    }

    public void disarm() {
        armed = false;
        comboUsed = false;
    }

    public boolean isArmed() {
        return armed;
    }

    public boolean shift() {
        return shift;
    }

    public boolean alt() {
        return alt;
    }

    public void onKeyPress(int key) {
        if (!armed) return;
        switch (key) {
            case Keyboard.KEY_F3, Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT, Keyboard.KEY_LMENU, Keyboard.KEY_RMENU -> {}
            default -> comboUsed = true;
        }
    }

    public boolean onF3Release() {
        final boolean toggle = armed && !comboUsed;
        disarm();
        return toggle;
    }
}
