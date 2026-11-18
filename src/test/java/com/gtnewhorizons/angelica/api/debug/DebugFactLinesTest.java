package com.gtnewhorizons.angelica.api.debug;

import com.gtnewhorizons.angelica.debug.screen.RecordingDisplayer;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DebugFactLinesTest {

    private static RecordingDisplayer record(DebugFactLines facts) {
        return RecordingDisplayer.of(facts);
    }

    private static RecordingDisplayer split(DebugFactLines facts, String line) {
        facts.begin();
        facts.split(line);
        facts.end();
        return record(facts);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Chunk Workers: 0/10 busy|Chunk Workers|0/10 busy",
        "cutout_mipped - 26 sections, 5.042 us|cutout_mipped|26 sections, 5.042 us",
        "abcdefghijklmnopqrstuvwx: x|abcdefghijklmnopqrstuvwx|x",
        "U=00 (P0=000): x|PLAIN|U=00 (P0=000): x",
        "abcdefghijklmnopqrstuvwxy: x|PLAIN|abcdefghijklmnopqrstuvwxy: x",
        "Shadow Pass|PLAIN|Shadow Pass",
        ": x|PLAIN|: x" })
    void splitsLinesIntoFactsOrLeavesThemPlain(String line, String name, String value) {
        final RecordingDisplayer recorder = split(new DebugFactLines(), line);
        assertEquals(name.equals("PLAIN") ? Collections.singletonList(null) : List.of(name), recorder.names);
        assertEquals(value, recorder.text(0));
    }

    @Test
    void equalSourceReusesInstancesAndChangedSourceResplits() {
        final DebugFactLines facts = new DebugFactLines();
        final String[] values = Reflect.get(facts, "values");
        final String[] names = Reflect.get(facts, "names");
        final RecordingDisplayer first = split(facts, "Queue: 12");
        final String firstValue = values[0];
        final String firstName = names[0];
        final RecordingDisplayer same = split(facts, new String("Queue: 12"));
        assertSame(first.names.get(0), same.names.get(0));
        assertSame(firstValue, values[0]);
        final RecordingDisplayer changed = split(facts, "Queue: 13");
        assertEquals("13", changed.text(0));
        assertNotSame(firstValue, values[0]);
        assertSame(firstName, names[0]);
    }

    @Test
    void endTruncatesStaleSlotsAndFactAfterSplitReplacesSlot() {
        final DebugFactLines facts = new DebugFactLines();
        facts.begin();
        facts.fact("A", "1");
        facts.fact("B", "2");
        facts.end();
        facts.begin();
        facts.fact("A", "1");
        facts.end();
        assertEquals(List.of("A"), record(facts).names);
        split(facts, "Queue: 12");
        facts.begin();
        facts.fact("Other", "x");
        facts.end();
        assertEquals(List.of("Other"), record(facts).names);
    }

    @Test
    void misuseThrowsAtTheCall() {
        final DebugFactLines facts = new DebugFactLines();
        assertThrows(IllegalStateException.class, () -> facts.fact("A", "1"));
        assertThrows(IllegalStateException.class, () -> facts.split("A: 1"));
        facts.begin();
        facts.fact("A", "1");
        assertThrows(IllegalArgumentException.class, () -> facts.fact("B", null));
        assertThrows(IllegalArgumentException.class, () -> facts.split(null));
        assertThrows(IllegalStateException.class, () -> record(facts));
        facts.end();
        assertThrows(IllegalStateException.class, () -> facts.fact("C", "3"));
        assertEquals(List.of("A"), record(facts).names);
    }
}
