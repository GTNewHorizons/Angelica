package com.gtnewhorizons.angelica.client.font;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontProviderMCTest {

    private FontProviderMC font;

    @BeforeEach
    void setUp() {
        font = FontProviderMC.get(false);
        // A spread of repeating widths, so every width has partners to swap between.
        final int[] widths = new int[256];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = 2 + (i % 7);
        }
        font.charWidth = widths;
    }

    /** Obfuscated text keeps its width, so the line does not move as glyphs re-roll. */
    @Test
    void replacementIsDrawnAtTheWidthItWasMatchedOn() {
        for (char chr = 'a'; chr <= 'z'; chr++) {
            final float expected = font.getXAdvance(chr);
            for (int i = 0; i < 500; i++) {
                final char replacement = font.getRandomReplacement(chr);
                assertEquals(expected, font.getXAdvance(replacement), 0.0f,
                    "'" + chr + "' was replaced by U+"
                        + String.format("%04X", (int) replacement) + " of another width");
            }
        }
    }

    @Test
    void replacementStillScrambles() {
        int changed = 0;
        for (int i = 0; i < 500; i++) {
            if (font.getRandomReplacement('a') != 'a') {
                changed++;
            }
        }
        assertTrue(changed > 0, "obfuscation never replaced the character");
    }

    @Test
    void charactersOutsideTheFontAreLeftAlone() {
        assertEquals('Я', font.getRandomReplacement('Я'));
    }
}
