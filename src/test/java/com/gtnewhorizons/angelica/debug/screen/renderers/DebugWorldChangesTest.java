package com.gtnewhorizons.angelica.debug.screen.renderers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DebugWorldChangesTest {

    private static DebugWorldChanges create() {
        final DebugWorldChanges changes = new DebugWorldChanges(4);
        changes.watch(0, 0, 0, 0, 15, 15, 15);
        changes.watch(2, 100, 60, 100, 120, 80, 120);
        changes.watch(3, 10, 0, 10, 30, 15, 30);
        return changes;
    }

    private static String dirty(DebugWorldChanges changes) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < 4; i++) out.append(changes.isDirty(i) ? '1' : '0');
        return out.toString();
    }

    @ParameterizedTest
    @CsvSource({
        "110,70,110,110,70,110,0010",
        "120,80,120,120,80,120,0010",
        "121,80,120,121,80,120,0000",
        "500,70,500,500,70,500,0000",
        "12,5,12,14,6,14,1001",
        "130,90,130,115,75,115,0010",
        "-30000000,0,-30000000,30000000,255,30000000,1011"})
    void rangeDirtiesOverlappingBoxesOnly(int x0, int y0, int z0, int x1, int y1, int z1, String expected) {
        final DebugWorldChanges changes = create();
        changes.markBlockRangeForRenderUpdate(x0, y0, z0, x1, y1, z1);
        assertEquals(expected, dirty(changes));
    }

    @Test
    void clearedBoxStopsMatching() {
        final DebugWorldChanges changes = create();
        changes.markBlockForUpdate(110, 70, 110);
        changes.clearBox(2);
        changes.markBlockForUpdate(110, 70, 110);
        assertFalse(changes.isDirty(2));
    }
}
