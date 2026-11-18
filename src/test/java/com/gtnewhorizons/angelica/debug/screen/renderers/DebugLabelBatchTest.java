package com.gtnewhorizons.angelica.debug.screen.renderers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugLabelBatchTest {

    @ParameterizedTest
    @CsvSource({
        "0,0,-5,true",
        "0,0,5,false",
        "0,0,-30,true",
        "0,0,-31,false",
        "3.9,0,-5,true",
        "4.1,0,-5,false",
        "-4.1,0,-5,false",
        "0,-4,-5,true",
        "0,4.2,-5,false"})
    void visibility(float x, float y, float z, boolean expected) {
        assertEquals(expected, DebugLabelBatch.isVisible(x, y, z, 0.06F, 0.16F, 1.4F, 1.4F, 1080));
    }

    @Test
    void digitsAreCenteredUsingPerDigitAdvance() {
        final float[] advance = new float[10];
        Arrays.fill(advance, 6.0F);
        advance[1] = 4.0F;
        assertEquals(10.0F, DebugLabelBatch.stringWidth(15, advance), 0.0F);
        assertEquals(-5.0F, DebugLabelBatch.digitLeft(15, 0, advance), 0.0F);
        assertEquals(-1.0F, DebugLabelBatch.digitLeft(15, 1, advance), 0.0F);
        assertEquals(-3.0F, DebugLabelBatch.digitLeft(7, 0, advance), 0.0F);
    }
}
