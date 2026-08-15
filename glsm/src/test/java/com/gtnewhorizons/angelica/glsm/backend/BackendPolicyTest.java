package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendPolicyTest {

    @Test
    void onlyImmediateCanTear() {
        assertTrue(VSyncMode.ON.tearFree());
        assertTrue(VSyncMode.MAILBOX.tearFree());
        assertFalse(VSyncMode.OFF.tearFree());
    }

    private static final Predicate<VSyncMode> NO_MAILBOX = mode -> mode != VSyncMode.MAILBOX;
    private static final Predicate<VSyncMode> EVERYTHING = mode -> true;

    @Test
    void mailboxIsTakenWhenTheBackendOffersIt() {
        assertEquals(VSyncMode.MAILBOX, RenderBackend.resolveTearFreeMode(null, EVERYTHING));
    }

    @Test
    void vsyncIsTheFallbackWhenMailboxIsUnsupported() {
        assertEquals(VSyncMode.ON, RenderBackend.resolveTearFreeMode(null, NO_MAILBOX));
        assertEquals(VSyncMode.ON, RenderBackend.resolveTearFreeMode(VSyncMode.MAILBOX, NO_MAILBOX));
    }

    @Test
    void anExplicitSupportedPreferenceWins() {
        assertEquals(VSyncMode.ON, RenderBackend.resolveTearFreeMode(VSyncMode.ON, EVERYTHING));
        assertEquals(VSyncMode.MAILBOX, RenderBackend.resolveTearFreeMode(VSyncMode.MAILBOX, EVERYTHING));
    }

    @Test
    void offIsNeverATearFreePreference() {
        assertEquals(VSyncMode.MAILBOX, RenderBackend.resolveTearFreeMode(VSyncMode.OFF, EVERYTHING));
        assertEquals(VSyncMode.ON, RenderBackend.resolveTearFreeMode(VSyncMode.OFF, NO_MAILBOX));
    }

    @Test
    void refreshPrefersTheExactRational() {
        assertEquals(144, RenderBackend.refreshHzFrom(144, 1, 0.0f));
        assertEquals(60, RenderBackend.refreshHzFrom(60000, 1001, 0.0f));
    }

    @Test
    void refreshRoundsUp() {
        assertEquals(144, RenderBackend.refreshHzFrom(0, 0, 143.98f));
        assertEquals(60, RenderBackend.refreshHzFrom(0, 0, 59.94f));
        assertEquals(60, RenderBackend.refreshHzFrom(0, 0, 60.0f));
    }

    @Test
    void refreshIsZeroWhenUnknown() {
        assertEquals(0, RenderBackend.refreshHzFrom(0, 0, 0.0f));
        assertEquals(0, RenderBackend.refreshHzFrom(-1, -1, -1.0f));
        assertEquals(0, RenderBackend.refreshHzFrom(144, 0, 0.0f));
    }
}
