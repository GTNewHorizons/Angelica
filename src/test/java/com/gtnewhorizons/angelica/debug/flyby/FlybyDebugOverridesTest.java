package com.gtnewhorizons.angelica.debug.flyby;

import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlybyDebugOverridesTest {

    @Test
    void parsesPairsToleratingWhitespace() {
        final List<FlybyDebugOverrides.Pair> pairs = FlybyDebugOverrides.parseEntries("  a:b = alwaysOn , c:d=never,e:f=inOverlay ");
        assertEquals(List.of("a:b", "c:d", "e:f"), pairs.stream().map(FlybyDebugOverrides.Pair::id).toList());
        assertEquals(List.of(DebugScreenEntryStatus.ALWAYS_ON, DebugScreenEntryStatus.NEVER, DebugScreenEntryStatus.IN_OVERLAY), pairs.stream().map(FlybyDebugOverrides.Pair::status).toList());
    }

    @Test
    void skipsMalformedPairsAndKeepsTheRest() {
        final List<FlybyDebugOverrides.Pair> pairs = FlybyDebugOverrides.parseEntries("x,a:b=bogus,=never,e:f=inOverlay");
        assertEquals(1, pairs.size());
        assertEquals("e:f", pairs.get(0).id());
        assertTrue(FlybyDebugOverrides.parseEntries("").isEmpty());
    }
}
