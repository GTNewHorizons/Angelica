package com.gtnewhorizons.angelica.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngelicaCommandTest {

    @Test
    void worldAlteringViewsNeedCheats() {
        assertTrue(AngelicaCommand.requiresCheats("wireframe"));
        assertTrue(AngelicaCommand.requiresCheats("flyby"));
    }

    @Test
    void diagnosticsNeverNeedCheats() {
        assertFalse(AngelicaCommand.requiresCheats("profile"));
        assertFalse(AngelicaCommand.requiresCheats("fog"));
        assertFalse(AngelicaCommand.requiresCheats("minimap"));
        assertFalse(AngelicaCommand.requiresCheats("help"));
    }
}
