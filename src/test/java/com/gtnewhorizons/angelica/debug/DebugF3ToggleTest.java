package com.gtnewhorizons.angelica.debug;

import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugF3ToggleTest {

    @Test
    void armedReleaseTogglesOnceAndModifiersDoNotUseTheCombo() {
        final DebugF3Toggle toggle = new DebugF3Toggle();
        assertFalse(toggle.onF3Release());
        toggle.arm(false, false);
        toggle.onKeyPress(Keyboard.KEY_LSHIFT);
        toggle.onKeyPress(Keyboard.KEY_RMENU);
        toggle.onKeyPress(Keyboard.KEY_F3);
        assertTrue(toggle.onF3Release());
        assertFalse(toggle.onF3Release());
    }

    @Test
    void otherKeyUsesTheComboUntilRearmed() {
        final DebugF3Toggle toggle = new DebugF3Toggle();
        toggle.onKeyPress(Keyboard.KEY_B);
        toggle.arm(false, false);
        toggle.onKeyPress(Keyboard.KEY_B);
        assertFalse(toggle.onF3Release());
        toggle.arm(false, false);
        assertTrue(toggle.onF3Release());
    }

    @Test
    void modifiersAreCapturedAtArmAndDisarmCancels() {
        final DebugF3Toggle toggle = new DebugF3Toggle();
        toggle.arm(true, false);
        assertTrue(toggle.onF3Release());
        assertTrue(toggle.shift());
        assertFalse(toggle.alt());
        toggle.arm(false, true);
        toggle.disarm();
        assertFalse(toggle.onF3Release());
    }
}
