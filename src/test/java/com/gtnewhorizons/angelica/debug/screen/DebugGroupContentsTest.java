package com.gtnewhorizons.angelica.debug.screen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugGroupContentsTest {

    private static final int TEXT = DebugGroupContents.TEXT;
    private static final int SIZE = DebugGroupContents.SIZE;
    private static final int ROW = DebugGroupContents.ROW_HEIGHT;

    private final FixedWidthText text = new FixedWidthText();
    private final DebugGroupContents contents = new DebugGroupContents(DebugGroup.POSITION);

    private int measure(int tick, String... lines) {
        contents.reset();
        for (final String line : lines) {
            if (line.contains("=")) {
                contents.addFact(line.substring(0, line.indexOf('=')).intern()).text.append(line, line.indexOf('=') + 1, line.length());
            } else if (!line.isEmpty()) {
                contents.addLine().text.append(line);
            }
        }
        return contents.measure(text, 200, false, tick);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "alpha|beta,  alpha|beta,  0",
        "alpha|beta,  gamma|beta,  1",
        "alpha|beta,  alpha,       1",
        "alpha,       alpha|beta,  3",
        "alpha,       '',          3",
        "Name=value,  Name=value,  0",
        "Name=value,  Nome=value,  1" })
    void changeFlagsFollowTheContent(String first, String second, int flags) {
        assertEquals(TEXT | SIZE, measure(0, first.split("\\|")));
        assertEquals(flags, measure(1, second.split("\\|")));
    }

    @Test
    void shrinkIsHeldForFortyTicksUnlessReleased() {
        measure(0, "alpha", "beta");
        assertEquals(TEXT, measure(1, "alpha"));
        for (int tick = 2; tick < 40; tick++) assertEquals(0, measure(tick, "alpha"), "tick " + tick);
        assertEquals(2 * ROW, contents.fullHeight());
        assertEquals(SIZE, measure(40, "alpha"));
        assertEquals(ROW, contents.fullHeight());

        measure(41, "alpha", "beta");
        assertEquals(TEXT, measure(42, "alpha"));
        contents.releaseSizeHold();
        assertEquals(SIZE, measure(43, "alpha"));
        assertEquals(ROW, contents.fullHeight());
    }

    @Test
    void titleAndForcedRemeasureAreText() {
        measure(0, "Name=value");
        contents.setTitle("Title", 3);
        assertEquals(TEXT | SIZE, measure(1, "Name=value"));
        contents.setTitle("Title", 3);
        assertEquals(0, measure(2, "Name=value"));
        contents.reset();
        contents.addFact("Name").text.append("value");
        assertEquals(TEXT, contents.measure(text, 200, true, 3));
    }
}
