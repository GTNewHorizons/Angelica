package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.rendering.ReducerStateMachine.State;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReducerStateMachineTest {

    private static final float DELTA = 1e-6f;

    private long time = 1_000_000_000L;
    private final ReducerStateMachine machine = new ReducerStateMachine(() -> time);

    @Test
    void configOffWinsOverEverything() {
        machine.toggleForced();
        assertTrue(machine.evaluate(false, false, false, 1_000_000_000L));
        assertEquals(State.DISABLED, machine.state());
    }

    @Test
    void keybindDisabledWinsOverForced() {
        machine.toggleForced();
        machine.toggleKeybindDisabled();
        machine.evaluate(true, true, true, 0);
        assertEquals(State.DISABLED, machine.state());
    }

    @Test
    void forcedWinsOverMinimized() {
        machine.toggleForced();
        machine.evaluate(true, false, true, 0);
        assertEquals(State.FORCED, machine.state());
    }

    @Test
    void minimizedWinsOverUnfocused() {
        machine.evaluate(true, false, false, 0);
        assertEquals(State.MINIMIZED, machine.state());
    }

    @Test
    void unfocusedWhenNotActiveButVisible() {
        machine.evaluate(true, true, false, 0);
        assertEquals(State.UNFOCUSED, machine.state());

        time += 10_000_000_000L;
        machine.evaluate(true, true, false, 1_000_000_000L);
        assertEquals(State.UNFOCUSED, machine.state());
    }

    @Test
    void idleAfterTimeoutWhileFocused() {
        time += 999_999_999L;
        assertFalse(machine.evaluate(true, true, true, 1_000_000_000L));
        assertEquals(State.FOCUSED, machine.state());

        time += 1L;
        assertTrue(machine.evaluate(true, true, true, 1_000_000_000L));
        assertEquals(State.IDLE, machine.state());
    }

    @Test
    void idleTimeoutZeroNeverIdles() {
        time += 10_000_000_000L;
        machine.evaluate(true, true, true, 0);
        assertEquals(State.FOCUSED, machine.state());
    }

    @Test
    void inputReturnsFromIdle() {
        time += 1_000_000_000L;
        machine.evaluate(true, true, true, 1_000_000_000L);
        assertEquals(State.IDLE, machine.state());

        machine.onInput();
        machine.evaluate(true, true, true, 1_000_000_000L);
        assertEquals(State.FOCUSED, machine.state());
    }

    @Test
    void toggleForcedCountsAsInput() {
        time += 10_000_000_000L;
        machine.evaluate(true, true, true, 1_000_000_000L);
        assertEquals(State.IDLE, machine.state());

        machine.toggleForced();
        machine.evaluate(true, true, true, 1_000_000_000L);
        assertEquals(State.FORCED, machine.state());

        machine.toggleForced();
        machine.evaluate(true, true, true, 1_000_000_000L);
        assertEquals(State.FOCUSED, machine.state());
    }

    @Test
    void toggleKeybindDisabledCountsAsInput() {
        time += 10_000_000_000L;
        machine.evaluate(true, true, true, 1_000_000_000L);
        assertEquals(State.IDLE, machine.state());

        machine.toggleKeybindDisabled();
        machine.evaluate(true, true, true, 1_000_000_000L);
        assertEquals(State.DISABLED, machine.state());

        machine.toggleKeybindDisabled();
        machine.evaluate(true, true, true, 1_000_000_000L);
        assertEquals(State.FOCUSED, machine.state());
    }

    @Test
    void evaluateReportsTransitionOnce() {
        assertFalse(machine.evaluate(true, true, true, 0));
        machine.toggleForced();
        assertTrue(machine.evaluate(true, true, true, 0));
        assertFalse(machine.evaluate(true, true, true, 0));
    }


    @Test
    void unlimitedUserAndNoStateCapIsUncapped() {
        assertEquals(0, ReducerStateMachine.mergeCap(0, 0, false, true));
        assertEquals(0, ReducerStateMachine.mergeCap(260, 0, false, true));
    }

    @Test
    void theLowerOfTheUserAndStateCapWins() {
        assertEquals(10, ReducerStateMachine.mergeCap(120, 10, false, true));
        assertEquals(30, ReducerStateMachine.mergeCap(30, 60, false, true));
    }

    @Test
    void menuCapAppliesOnlyWhenOptionOn() {
        assertEquals(30, ReducerStateMachine.mergeCap(0, 0, true, true));
        assertEquals(0, ReducerStateMachine.mergeCap(0, 0, true, false));
    }

    @Test
    void menuCapNeverRaisesUserCap() {
        assertEquals(20, ReducerStateMachine.mergeCap(20, 0, true, true));
        assertEquals(30, ReducerStateMachine.mergeCap(260, 0, true, true));
        assertEquals(10, ReducerStateMachine.mergeCap(260, 10, true, true));
    }

    @Test
    void stateCapSelectsProfile() {
        assertEquals(10, ReducerStateMachine.stateCap(State.FORCED, 10, 30));
        assertEquals(10, ReducerStateMachine.stateCap(State.UNFOCUSED, 10, 30));
        assertEquals(20, ReducerStateMachine.stateCap(State.MINIMIZED, 5, 5));
        assertEquals(20, ReducerStateMachine.stateCap(State.MINIMIZED, 260, 260));
        assertEquals(30, ReducerStateMachine.stateCap(State.IDLE, 10, 30));
        assertEquals(0, ReducerStateMachine.stateCap(State.FOCUSED, 10, 10));
        assertEquals(0, ReducerStateMachine.stateCap(State.DISABLED, 10, 10));
        assertEquals(0, ReducerStateMachine.stateCap(State.UNFOCUSED, 260, 30));
        assertEquals(0, ReducerStateMachine.stateCap(State.IDLE, 10, 260));
        assertEquals(120, ReducerStateMachine.mergeCap(120, ReducerStateMachine.stateCap(State.UNFOCUSED, 260, 30), false, true));
    }

    @Test
    void masterVolumeScalesByPercent() {
        assertEquals(0.73f, ReducerStateMachine.masterVolume(0.73f, 100), DELTA);
        assertEquals(0.5f, ReducerStateMachine.masterVolume(1.0f, 50), DELTA);
        assertEquals(0.01f, ReducerStateMachine.masterVolume(1.0f, 0), DELTA);
    }

    @Test
    void stateVolumeSelectsProfile() {
        assertEquals(10, ReducerStateMachine.stateVolume(State.FORCED, 10, 20, 30));
        assertEquals(10, ReducerStateMachine.stateVolume(State.UNFOCUSED, 10, 20, 30));
        assertEquals(20, ReducerStateMachine.stateVolume(State.MINIMIZED, 10, 20, 30));
        assertEquals(30, ReducerStateMachine.stateVolume(State.IDLE, 10, 20, 30));
        assertEquals(100, ReducerStateMachine.stateVolume(State.FOCUSED, 10, 20, 30));
        assertEquals(100, ReducerStateMachine.stateVolume(State.DISABLED, 10, 20, 30));
    }
}
