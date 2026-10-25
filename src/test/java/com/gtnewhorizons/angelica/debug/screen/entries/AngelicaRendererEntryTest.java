package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugFactLines;
import com.gtnewhorizons.angelica.debug.screen.RecordingDisplayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AngelicaRendererEntryTest {

    private static List<String> render(String line) {
        final DebugFactLines facts = new DebugFactLines();
        facts.begin();
        AngelicaRendererEntry.addRendererLine(facts, line);
        facts.end();
        final RecordingDisplayer displayer = RecordingDisplayer.of(facts);
        final List<String> rendered = new ArrayList<>();
        for (int i = 0; i < displayer.texts.size(); i++) {
            final String name = displayer.names.get(i);
            rendered.add(name == null ? displayer.text(i) : name + ": " + displayer.text(i));
        }
        return rendered;
    }

    @Test
    void bufferLineSplitsIntoGeometryAndIndex() {
        assertEquals(List.of("Geometry: 17/28 MiB", "Index: 0/0 MiB (10 buffers)"), render("G: 17/28, I: 0/0 MiB (10 buffers)"));
    }
}
