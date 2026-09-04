package com.gtnewhorizons.angelica.sdlgpu.frame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcquireAffinityTest {

    private static final Thread WINDOW = new Thread("window");
    private static final Thread OTHER = new Thread("other");

    @Test
    void acquiringOnTheWindowThreadIsTheOnlyAcceptedCase() {
        assertFalse(FrameManager.acquireAffinityViolated(WINDOW, WINDOW, 0));
    }

    @Test
    void acquiringOnAnyOtherThreadViolatesTheSdlContract() {
        assertTrue(FrameManager.acquireAffinityViolated(OTHER, WINDOW, 0));
    }

    @Test
    void aLaterAcquireFromASecondThreadViolatesEvenWhenTheFirstWasCorrect() {
        assertTrue(FrameManager.acquireAffinityViolated(WINDOW, WINDOW, 1),
            "drift after a correct first acquire must still be caught");
    }

    @Test
    void anUnknownWindowThreadCannotBeJudged() {
        assertFalse(FrameManager.acquireAffinityViolated(OTHER, null, 3));
    }
}
