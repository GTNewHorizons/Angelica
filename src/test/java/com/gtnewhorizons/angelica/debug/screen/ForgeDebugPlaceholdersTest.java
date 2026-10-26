package com.gtnewhorizons.angelica.debug.screen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeDebugPlaceholdersTest {

    private static final List<String> BRANDINGS = Arrays.asList("MCP 9.05", "Minecraft Forge 10.13.4.1614", "42 mods loaded");
    private static final String[] LEFT = { "Minecraft ", "C: ", "E: ", "P: ", "Overworld", null, "x: ", "y: ", "z: ", "f: ", "lc: ", "ws: ", "shader: " };

    private final ArrayList<String> left = new ArrayList<>();
    private final ArrayList<String> right = new ArrayList<>();

    private static long nulls(List<String> lines) {
        return lines.stream().filter(s -> s == null).count();
    }

    @ParameterizedTest(name = "chunkLoaded={0} shader={1}")
    @CsvSource({ "true, false", "false, false", "true, true" })
    void listsKeepTheVanillaShapeSoModIndicesStillLine(boolean chunkLoaded, boolean shader) {
        ForgeDebugPlaceholders.add(left, right, chunkLoaded, shader, BRANDINGS);
        assertEquals(shader ? 13 : 12, left.size());
        for (int i = 0; i < left.size(); i++) {
            if (LEFT[i] == null || i == 10 && !chunkLoaded) {
                assertNull(left.get(i), "left " + i);
            } else {
                assertTrue(left.get(i).startsWith(LEFT[i]), "left " + i + " = " + left.get(i));
                assertTrue(ForgeDebugPlaceholders.isPlaceholder(left.get(i)), "left " + i);
            }
        }
        assertTrue(right.get(0).startsWith("Used memory: "));
        assertTrue(right.get(1).startsWith("Allocated memory: "));
        assertNull(right.get(2));
        assertEquals(BRANDINGS, right.subList(3, right.size()));
        assertEquals(nulls(left), ForgeDebugPlaceholders.nullsAdded(0));
        assertEquals(nulls(right), ForgeDebugPlaceholders.nullsAdded(1));
    }

    @Test
    void onlyTheAddedInstancesArePlaceholders() {
        final String oldBranding = new String("Old branding");
        ForgeDebugPlaceholders.add(left, right, true, false, List.of(oldBranding));
        assertTrue(ForgeDebugPlaceholders.isPlaceholder(oldBranding));
        ForgeDebugPlaceholders.add(new ArrayList<>(), new ArrayList<>(), true, false, BRANDINGS);
        assertFalse(ForgeDebugPlaceholders.isPlaceholder(oldBranding));
        assertTrue(ForgeDebugPlaceholders.isPlaceholder(BRANDINGS.get(0)));
        assertFalse(ForgeDebugPlaceholders.isPlaceholder(new String(BRANDINGS.get(0))));
        assertFalse(ForgeDebugPlaceholders.isPlaceholder(new String(ForgeDebugPlaceholders.VERSION)));
        assertFalse(ForgeDebugPlaceholders.isPlaceholder("JourneyMap"));
        assertFalse(ForgeDebugPlaceholders.isPlaceholder(null));
    }
}
