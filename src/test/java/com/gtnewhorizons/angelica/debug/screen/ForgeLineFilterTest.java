package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.config.CompatConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeLineFilterTest {

    private static final List<String> BRANDINGS = Arrays.asList("MCP 9.05", "FML v7.10.99", "Minecraft Forge 10.13.4.1614", "42 mods loaded");
    private static final String[] HODGEPODGE = { "Allocation rate: 3MB/s", "IntCache: 1, 2, 3", "LWJGL: 3.3.3", "Java: 21 64bit", "GPU: Apple M1", "OpenGL: 3.3", "CPU Cores: 8", "OS:  Mac OS X" };

    private final ArrayList<String> left = new ArrayList<>();
    private final ArrayList<String> right = new ArrayList<>();
    private final HashMap<Integer, List<String>> out = new HashMap<>();
    private final ForgeLineFilter.Sink sink = (group, source, start, name) -> out.computeIfAbsent(group, g -> new ArrayList<>())
        .add(source == null ? "" : name == null ? source.substring(start) : name + "=" + source.substring(start));

    private final DebugRegistry registry = new DebugRegistry();
    private ForgeLineFilter filter;
    private DebugGroup memoryGroup;
    private DebugGroup specsGroup;
    private DebugGroup positionGroup;
    private DebugGroup otherLeft;
    private DebugGroup otherRight;
    private DebugEntryDef memory;
    private DebugEntryDef specs;
    private DebugEntryDef gateRight;
    private String[] savedRules;

    @BeforeEach
    void setUp() {
        savedRules = CompatConfig.debugLineRules;
        CompatConfig.debugLineRules = new String[0];
        memoryGroup = TestRegistry.group(registry, "angelica:memory", DebugGroupHandle.RIGHT, 100);
        specsGroup = TestRegistry.group(registry, "angelica:system_specs", DebugGroupHandle.RIGHT, 200);
        otherRight = TestRegistry.group(registry, "angelica:other_right", DebugGroupHandle.RIGHT, 800);
        positionGroup = TestRegistry.group(registry, "angelica:position", DebugGroupHandle.LEFT, 200);
        otherLeft = TestRegistry.group(registry, "angelica:other_left", DebugGroupHandle.LEFT, 800);
        memory = registry.registerEntry(TestRegistry.text("minecraft:memory"));
        specs = registry.registerEntry(TestRegistry.text("minecraft:system_specs"));
        registry.registerEntry(TestRegistry.text("minecraft:player_position"));
        registry.registerEntry(TestRegistry.text("angelica:other_left"));
        gateRight = registry.registerEntry(TestRegistry.text("angelica:other_right"));
        registry.freeze();
        for (int i = 0; i < registry.entryCount(); i++) registry.entryAt(i).enabled = true;
        filter = new ForgeLineFilter(registry);
        ForgeDebugPlaceholders.add(left, right, true, false, BRANDINGS);
    }

    @AfterEach
    void restoreRules() {
        CompatConfig.debugLineRules = savedRules;
    }

    private void sample() {
        out.clear();
        filter.sample(left, right, sink);
    }

    private List<String> in(DebugGroup group) {
        return out.getOrDefault(group.index(), Collections.emptyList());
    }

    private static List<String> list(String... lines) {
        return Arrays.asList(lines);
    }

    @Test
    void placeholdersAloneProduceNothingButEqualContentCopiesAreThirdParty() {
        sample();
        assertTrue(out.isEmpty());
        final String copy = new String(ForgeDebugPlaceholders.X);
        left.set(left.indexOf(ForgeDebugPlaceholders.X), copy);
        sample();
        assertEquals(list(copy), in(otherLeft));
    }

    @Test
    void defaultsDropAndRouteWhileTheirEntriesAreEnabled() {
        right.addAll(Arrays.asList(HODGEPODGE));
        sample();
        assertEquals(list("IntCache=1, 2, 3"), in(memoryGroup));
        assertEquals(list("LWJGL=3.3.3", "OS=Mac OS X"), in(specsGroup));
        assertEquals(2, out.size());
        specs.enabled = false;
        memory.enabled = false;
        sample();
        assertEquals(list(HODGEPODGE), in(otherRight));
        assertEquals(1, out.size());
    }

    @Test
    void otherGatesDropOnlyOtherLinesAndKeepRoutedFacts() {
        left.addAll(List.of("Cube X: 4", "mod a"));
        right.addAll(List.of("IntCache: 1", "mod r"));
        gateRight.enabled = false;
        sample();
        assertEquals(list("Cube X=4"), in(positionGroup));
        assertEquals(list("mod a"), in(otherLeft));
        assertEquals(list("IntCache=1"), in(memoryGroup));
        assertEquals(Collections.emptyList(), in(otherRight));
    }

    @Test
    void forgeNullsAreConsumedAndOtherBlanksSeparate() {
        CompatConfig.debugLineRules = new String[] { "drop right \"dropped\"" };
        left.add(1, "pack 1.0");
        left.addAll(Arrays.asList("mod a", null, "mod b"));
        right.addAll(Arrays.asList(null, "", "a", null, "dropped 1", "   ", "\u00a7r", "b", "IntCache: 1", "", "dropped 2", null));
        sample();
        assertEquals(list("pack 1.0", "mod a", "", "mod b"), in(otherLeft));
        assertEquals(list("a", "", "b"), in(otherRight));
        assertEquals(list("IntCache=1"), in(memoryGroup));
        left.clear();
        left.addAll(Arrays.asList("mod a", null, "mod b"));
        sample();
        assertEquals(list("mod a", "", "mod b"), in(otherLeft));
    }

    @Test
    void groupGetsTheLeftListThenTheRightListInListOrder() {
        CompatConfig.debugLineRules = new String[] { "fact right \"R\" group=angelica:position name=Right", "fact left \"L\" group=angelica:position name=Left" };
        right.addAll(List.of("R1", "R2"));
        left.addAll(List.of("L1", "L2"));
        sample();
        assertEquals(list("Left=1", "Left=2", "Right=1", "Right=2"), in(positionGroup));
    }

    @Test
    void replacingTheConfigArrayRecompiles() {
        left.addAll(List.of("mod a", "mod b"));
        sample();
        assertEquals(list("mod a", "mod b"), in(otherLeft));
        CompatConfig.debugLineRules = new String[] { "drop left \"mod a\"", "fact left \"mod \" group=angelica:position name=Mod" };
        sample();
        assertEquals(Collections.emptyList(), in(otherLeft));
        assertEquals(list("Mod=b"), in(positionGroup));
        CompatConfig.debugLineRules = new String[0];
        sample();
        assertEquals(list("mod a", "mod b"), in(otherLeft));
    }
}
