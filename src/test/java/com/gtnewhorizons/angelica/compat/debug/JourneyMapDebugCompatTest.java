package com.gtnewhorizons.angelica.compat.debug;

import com.gtnewhorizons.angelica.api.debug.DebugFactLines;
import com.gtnewhorizons.angelica.debug.screen.RecordingDisplayer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JourneyMapDebugCompatTest {

    private static RecordingDisplayer display(String... stats) {
        return display(stats, null, null);
    }

    private static RecordingDisplayer display(String[] stats, String mappingDisabled, List<String> report) {
        final DebugFactLines facts = new DebugFactLines();
        JourneyMapDebugEntry.writeFacts(facts, stats, mappingDisabled, report);
        return RecordingDisplayer.of(facts);
    }

    @Test
    void statLinesBecomeNamedFactsAndOthersStayPlain() {
        final RecordingDisplayer displayer = display("Surface: 4 (69) + 5 (28) = 97 chunks in 36ms (avg 0.4ms)", "Caves: 3 = 12 chunks in 5ms", "no stats yet");
        assertEquals("[Surface, Caves, null]", displayer.names.toString());
        assertEquals("4 (69) + 5 (28) = 97 chunks in 36ms (avg 0.4ms)", displayer.text(0));
        assertEquals("3 = 12 chunks in 5ms", displayer.text(1));
        assertEquals("no stats yet", displayer.text(2));
    }

    @Test
    void disabledMappingShowsItsNoticeAndProfilerReportLinesStayPlain() {
        final RecordingDisplayer displayer = display(null, "Mapping disabled.", Arrays.asList("MapPlayerTask: count 3 avg 1.5ms", "Render: count 9"));
        assertEquals("[null, null, null]", displayer.names.toString());
        assertEquals("Mapping disabled.", displayer.text(0));
        assertEquals("MapPlayerTask: count 3 avg 1.5ms", displayer.text(1));
        assertEquals("Render: count 9", displayer.text(2));
    }
}
