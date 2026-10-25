package com.gtnewhorizons.angelica.debug.screen;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugTextWrapTest {

    @ParameterizedTest(name = "\"{0}\" in {1}")
    @CsvSource(delimiter = ';', value = {
        "short line; 20; short line",
        "aaa bbb ccc ddd; 8; aaa bbb|ccc ddd",
        "abcdefghij; 4; abcd|efgh|ij",
        "&a&lab cd&r ef gh; 6; &a&lab|&a&lcd&r|ef gh",
        "&a&o&lab cd; 6; &a&o&lab|&a&l&ocd",
        "abc&cdef; 3; abc&c|&cdef",
        "' abcdef'; 4; ' abc|def'",
        "''; 10; ''" })
    void wrapsAtSpacesAndCarriesFormatOntoContinuationRows(String text, int budget, String rows) {
        final DebugGroupContents.Slot slot = new DebugGroupContents.Slot();
        slot.begin();
        slot.text.append(text.replace('&', '\u00a7'));
        slot.measure(new FixedWidthText(), budget, false);
        final List<String> actual = new ArrayList<>();
        for (int i = 0; i < slot.rowCount; i++) actual.add(slot.rowText().subSequence(slot.rowStart[i], slot.rowEnd[i]).toString().replace('\u00a7', '&'));
        assertEquals(List.of(rows.split("\\|", -1)), actual);
    }
}
