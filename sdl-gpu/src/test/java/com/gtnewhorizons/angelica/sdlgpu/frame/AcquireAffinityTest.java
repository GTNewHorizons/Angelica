package com.gtnewhorizons.angelica.sdlgpu.frame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcquireAffinityTest {

    private static final Thread WINDOW = new Thread("window");
    private static final Thread OTHER = new Thread("other");

    @Test
    void acquiringOnTheWindowThreadIsTheOnlyAcceptedCase() {
        assertFalse(FrameManager.acquireAffinityViolated(WINDOW, WINDOW));
    }

    @Test
    void acquiringOnAnyOtherThreadViolatesTheSdlContract() {
        assertTrue(FrameManager.acquireAffinityViolated(OTHER, WINDOW));
    }

    @Test
    void anUnknownWindowThreadCannotBeJudged() {
        assertFalse(FrameManager.acquireAffinityViolated(OTHER, null),
            "the check runs before the window is claimed; judging a null window would throw at startup");
    }
}
