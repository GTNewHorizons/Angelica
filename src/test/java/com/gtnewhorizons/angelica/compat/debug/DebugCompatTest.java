package com.gtnewhorizons.angelica.compat.debug;

import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec;
import com.gtnewhorizons.angelica.debug.screen.DebugEntryDef;
import com.gtnewhorizons.angelica.debug.screen.DebugRegistry;
import com.gtnewhorizons.angelica.debug.screen.DebugScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugCompatTest {

    private int runs;

    @Test
    void skipsNamespaceThatIsAlreadyRegistered() {
        DebugScreen.bootstrap();
        DebugCompat.registerMissing("minecraft", () -> runs++);
        assertEquals(0, runs);
    }

    @Test
    void shimStandsDownWhenTheDebugScreenFailedToStart() {
        final DebugRegistry registry = new DebugRegistry();
        final DebugEntryDef def = registry.registerEntry(new DebugEntrySpec("compattest:entry", DebugEntryCategory.SCREEN_TEXT));
        assertTrue(DebugCompat.active(def));
        registry.failAll();
        assertFalse(DebugCompat.active(def));
    }

    @Test
    void failingRegistrationDoesNotStopTheNext() {
        DebugCompat.registerMissing("compattest", () -> {
            throw new NoClassDefFoundError("vazkii/botania/Missing");
        });
        DebugCompat.registerMissing("compattest", () -> runs++);
        assertEquals(1, runs);
    }
}
