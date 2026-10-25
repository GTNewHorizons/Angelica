package com.gtnewhorizons.angelica.debug.screen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugLayoutTest {

    private static final int S = DebugLayout.GROUP_SPACING;
    private static final DebugRegistry REGISTRY = TestRegistry.main();

    private final DebugLayout layout = new DebugLayout(REGISTRY);

    private static DebugGroup group(String name) throws ReflectiveOperationException {
        return (DebugGroup) DebugGroup.class.getField(name).get(null);
    }

    private void present(DebugGroup... groups) {
        Arrays.fill(layout.height, 0);
        for (final DebugGroup group : groups) layout.height[group.index] = 10;
    }

    private int top(DebugGroup group) {
        return layout.top[group.index];
    }

    @ParameterizedTest(name = "{0} max={1},{2}")
    @CsvSource({
        "RENDERING SHADERS,         1000, 1000, RENDERING:L:2 SHADERS:L:21 POSITION:-",
        "RENDERING POSITION MEMORY, 15,   1000, RENDERING:L:2 MEMORY:R:2 POSITION:R:21",
        "RENDERING POSITION MEMORY, 25,   25,   RENDERING:L:2 MEMORY:R:2 POSITION:L:21",
        "MEMORY,                    1000, 0,    MEMORY:L:2",
        "POSITION MEMORY,           0,    0,    POSITION:- MEMORY:-",
        "PRIORITY_LEFT,             0,    0,    PRIORITY_LEFT:L:2" })
    void groupsOverflowToTheOtherColumnThenClip(String groups, int leftMax, int rightMax, String expected) throws ReflectiveOperationException {
        final String[] names = groups.split(" ");
        final DebugGroup[] present = new DebugGroup[names.length];
        for (int i = 0; i < names.length; i++) present[i] = group(names[i]);
        present(present);
        layout.assign(2, leftMax, rightMax);
        for (final String placement : expected.split(" ")) {
            final String[] parts = placement.split(":");
            final DebugGroup group = group(parts[0]);
            assertEquals(parts[1].equals("-") ? -1 : parts[1].equals("L") ? DebugGroup.LEFT : DebugGroup.RIGHT, layout.side[group.index], placement);
            if (parts.length > 2) assertEquals(Integer.parseInt(parts[2]), top(group), placement);
        }
    }

    @Test
    void allGroupsStackInRegistryOrderOnTheirHomeSide() {
        final DebugGroup[] all = new DebugGroup[REGISTRY.groupCount()];
        for (int i = 0; i < all.length; i++) all[i] = REGISTRY.groupAt(i);
        present(all);
        layout.assign(2, 1000, 1000);
        final int[] next = { 2, 2 };
        for (final DebugGroup group : all) {
            assertEquals(group.side, layout.side[group.index], group.id);
            assertEquals(next[group.side], top(group), group.id);
            next[group.side] += 10 + S;
        }
        assertEquals(2, top(DebugGroup.PRIORITY_LEFT));
        assertEquals(next[DebugGroup.LEFT] - S - 10, top(DebugGroup.HELP));
        assertEquals(next[DebugGroup.RIGHT] - S - 10, top(DebugGroup.LOOKING_AT_BLOCK));
    }

    @Test
    void appearingGroupDoesNotMoveEarlierGroups() {
        present(DebugGroup.MEMORY, DebugGroup.ANGELICA);
        layout.assign(2, 1000, 1000);
        final int memory = top(DebugGroup.MEMORY);
        final int angelica = top(DebugGroup.ANGELICA);
        present(DebugGroup.MEMORY, DebugGroup.ANGELICA, DebugGroup.LOOKING_AT_BLOCK);
        layout.assign(2, 1000, 1000);
        assertEquals(memory, top(DebugGroup.MEMORY));
        assertEquals(angelica, top(DebugGroup.ANGELICA));
        assertTrue(top(DebugGroup.LOOKING_AT_BLOCK) > angelica);
    }

    @Test
    void singleColumnTakesEveryGroupInOrderUntilFull() {
        present(DebugGroup.PRIORITY_LEFT, DebugGroup.RENDERING, DebugGroup.POSITION, DebugGroup.MEMORY);
        layout.assignSingleColumn(DebugGroup.RIGHT, 2, 50);
        assertEquals(DebugGroup.RIGHT, layout.side[DebugGroup.PRIORITY_LEFT.index]);
        assertEquals(2, top(DebugGroup.PRIORITY_LEFT));
        assertEquals(2 + 10 + S, top(DebugGroup.RENDERING));
        assertEquals(2 + 2 * (10 + S), top(DebugGroup.POSITION));
        assertEquals(-1, layout.side[DebugGroup.MEMORY.index]);
    }
}
