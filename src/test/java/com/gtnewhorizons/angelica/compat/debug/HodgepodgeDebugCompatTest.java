package com.gtnewhorizons.angelica.compat.debug;

import com.gtnewhorizons.angelica.debug.screen.RecordingDisplayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HodgepodgeDebugCompatTest {

    private static RecordingDisplayer write() {
        final RecordingDisplayer displayer = new RecordingDisplayer();
        HodgepodgeDebugEntry.writeFacts(displayer, RecordingDisplayer.GROUP, 3.26, 20.0, 1.5, 12.04, 140, 32, 9, 4, 1, 2, 0, 17, 105.75);
        return displayer;
    }

    @Test
    void writesSixFactsFromTheStats() {
        final RecordingDisplayer displayer = write();
        assertEquals("[Budget, Average, Queues, Active, Blocked, Debt]", displayer.names.toString());
        assertEquals("3.3 / 20.0 ms", displayer.text(0));
        assertEquals("terrain 1.5 ms, populate 12.0 ms", displayer.text(1));
        assertEquals("gen 140, populate 32, pipeline 9", displayer.text(2));
        assertEquals("gen 4, populate 1", displayer.text(3));
        assertEquals("2 blocked, 0 stuck, 17 fills", displayer.text(4));
        assertEquals("105.8 ms", displayer.text(5));
    }
}
