package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendPolicyTest {

    @Test
    void autoFollowsTheVsyncToggle() {
        assertEquals(VSyncMode.ON, VSyncMode.AUTO.resolve(true));
        assertEquals(VSyncMode.OFF, VSyncMode.AUTO.resolve(false));
    }

    @Test
    void anExplicitModeIgnoresTheVsyncToggle() {
        for (VSyncMode mode : new VSyncMode[]{ VSyncMode.ON, VSyncMode.MAILBOX, VSyncMode.OFF }) {
            assertEquals(mode, mode.resolve(true));
            assertEquals(mode, mode.resolve(false));
        }
    }

    @Test
    void onlyOnBlocks() {
        assertTrue(VSyncMode.ON.blocksOnVBlank());
        assertFalse(VSyncMode.MAILBOX.blocksOnVBlank());
        assertFalse(VSyncMode.OFF.blocksOnVBlank());
    }

    @Test
    void mailboxRidesOnSwapIntervalOne() {
        assertTrue(VSyncMode.ON.usesVSyncSwapInterval());
        assertTrue(VSyncMode.MAILBOX.usesVSyncSwapInterval());
        assertFalse(VSyncMode.OFF.usesVSyncSwapInterval());
    }

    @Test
    void swapIntervalBackendsReportWhatTheyActuallyGet() {
        assertEquals(VSyncMode.ON, VSyncMode.MAILBOX.swapIntervalEquivalent());
        assertEquals(VSyncMode.ON, VSyncMode.ON.swapIntervalEquivalent());
        assertEquals(VSyncMode.OFF, VSyncMode.OFF.swapIntervalEquivalent());
    }

    @Test
    void fallbackNeverWidensTearing() {
        assertEquals(List.of(VSyncMode.ON), VSyncMode.ON.fallbackOrder());
        assertEquals(List.of(VSyncMode.MAILBOX, VSyncMode.ON), VSyncMode.MAILBOX.fallbackOrder());
        assertEquals(List.of(VSyncMode.OFF, VSyncMode.MAILBOX, VSyncMode.ON), VSyncMode.OFF.fallbackOrder());
    }

    @Test
    void onlyOffCanTear() {
        assertTrue(VSyncMode.ON.tearFree());
        assertTrue(VSyncMode.MAILBOX.tearFree());
        assertFalse(VSyncMode.OFF.tearFree());
    }

    @Test
    void onlyBackendSupportedModesAreSelectable() {
        assertEquals(List.of(VSyncMode.OFF, VSyncMode.ON), VSyncMode.selectable(m -> m != VSyncMode.MAILBOX));
        assertEquals(List.of(VSyncMode.OFF, VSyncMode.ON, VSyncMode.MAILBOX), VSyncMode.selectable(m -> true));
    }

    @Test
    void autoIsNeverSelectable() {
        assertFalse(VSyncMode.selectable(m -> true).contains(VSyncMode.AUTO));
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

    @Test
    void glRenderAheadKeepsZeroAsOff() {
        assertEquals(0, RenderBackend.clampRenderAhead(0, 0, 9));
        assertEquals(0, RenderBackend.clampRenderAhead(-1, 0, 9));
    }

    @Test
    void glRenderAheadClampsToItsRange() {
        assertEquals(1, RenderBackend.clampRenderAhead(1, 0, 9));
        assertEquals(3, RenderBackend.clampRenderAhead(3, 0, 9));
        assertEquals(9, RenderBackend.clampRenderAhead(9, 0, 9));
        assertEquals(9, RenderBackend.clampRenderAhead(20, 0, 9));
    }

    @Test
    void sdlRenderAheadHasNoOffSwitch() {
        assertEquals(3, RenderBackend.clampRenderAhead(0, 1, 3));
        assertEquals(3, RenderBackend.clampRenderAhead(-1, 1, 3));
    }

    @Test
    void sdlRenderAheadClampsToItsRange() {
        assertEquals(1, RenderBackend.clampRenderAhead(1, 1, 3));
        assertEquals(2, RenderBackend.clampRenderAhead(2, 1, 3));
        assertEquals(3, RenderBackend.clampRenderAhead(3, 1, 3));
        assertEquals(3, RenderBackend.clampRenderAhead(6, 1, 3));
    }
}
