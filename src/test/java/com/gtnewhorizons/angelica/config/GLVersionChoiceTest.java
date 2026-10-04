package com.gtnewhorizons.angelica.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GLVersionChoiceTest {

    @Test
    void zeroAndBelowTheFloorAreAuto() {
        assertEquals(GLVersionChoice.AUTO, GLVersionChoice.of(0, 46));
        assertEquals(GLVersionChoice.AUTO, GLVersionChoice.of(32, 46));
    }

    @Test
    void nonexistentVersionsRoundDown() {
        assertEquals(GLVersionChoice.GL33, GLVersionChoice.of(34, 46));
        assertEquals(GLVersionChoice.GL33, GLVersionChoice.of(39, 46));
        assertEquals(GLVersionChoice.GL45, GLVersionChoice.of(45, 46));
    }

    @Test
    void ceilingCaps() {
        assertEquals(GLVersionChoice.GL41, GLVersionChoice.of(46, 41));
        assertEquals(GLVersionChoice.GL33, GLVersionChoice.of(41, 33));
    }
}
