package com.gtnewhorizons.angelica.debug.screen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlatDebugDisplayerTest {

    private final FlatDebugDisplayer displayer = new FlatDebugDisplayer(TestRegistry.main());
    private final ArrayList<String> left = new ArrayList<>();
    private final ArrayList<String> right = new ArrayList<>();

    @Test
    void groupsRouteBySideWithSpacersBetweenNonEmptyBlocks() {
        left.add("forge left");
        right.add("forge right");
        displayer.beginLines();
        displayer.line(DebugGroup.POSITION).text("pos");
        displayer.fact(DebugGroup.SHADERS, "Server").value(20).text(" ms");
        displayer.line(DebugGroup.SHADERS).text("shader line");
        displayer.line(DebugGroup.ANGELICA).text("renderer");
        displayer.fact(DebugGroup.ANGELICA, "Fog").value(1.5, 1);
        displayer.line(DebugGroup.MEMORY).text("mem");
        displayer.endLines();
        displayer.addTo(left, right);
        assertEquals(Arrays.asList("forge left", null, "pos", null, "Server: \u00a7f20\u00a7r ms", "shader line"), left);
        assertEquals(Arrays.asList("forge right", null, "renderer", "Fog: \u00a7f1.5\u00a7r", null, "mem"), right);
    }

    @Test
    void priorityGoesFirstWithoutALeadingSpacerAndNoLinesAddNothing() {
        displayer.beginLines();
        displayer.line(DebugGroup.PRIORITY_LEFT).text("Minecraft");
        displayer.line(DebugGroup.SHADERS).text("pack");
        displayer.endLines();
        displayer.addTo(left, right);
        assertEquals(Arrays.asList("Minecraft", null, "pack"), left);
        assertEquals(List.of(), right);
        displayer.beginLines();
        displayer.endLines();
        displayer.addTo(left, right);
        assertEquals(3, left.size());
    }
}
