package com.gtnewhorizons.angelica.commands;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AngelicaCommandTest {

    @ParameterizedTest(name = "{0} needs cheats: {1}")
    @CsvSource({
        "wireframe, true",
        "flyby, true",
        "profile, false",
        "fog, false",
        "minimap, false",
        "help, false",
    })
    void onlyWorldAlteringViewsNeedCheats(String subcommand, boolean expected) {
        assertEquals(expected, AngelicaCommand.requiresCheats(subcommand));
    }
}
