package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DebugLineRulesTest {

    private static final int LEFT = DebugGroupHandle.LEFT;
    private static final int RIGHT = DebugGroupHandle.RIGHT;

    private final DebugRegistry registry = new DebugRegistry();
    private DebugGroup memoryGroup;
    private DebugEntryDef memory;
    private DebugEntryDef specs;

    @BeforeEach
    void setUp() {
        memoryGroup = TestRegistry.group(registry, "angelica:memory", RIGHT, 100);
        TestRegistry.group(registry, "angelica:position", LEFT, 200);
        memory = registry.registerEntry(TestRegistry.text("minecraft:memory"));
        specs = registry.registerEntry(TestRegistry.text("minecraft:system_specs"));
        registry.freeze();
        memory.enabled = true;
        specs.enabled = true;
    }

    private DebugLineRules compile(String... config) {
        return DebugLineRules.compile(registry, config);
    }

    @Test
    void everyDefaultParsesAgainstTheBuiltinRegistry() {
        final DebugRegistry main = TestRegistry.main();
        final boolean[] saved = new boolean[main.entryCount()];
        for (int i = 0; i < saved.length; i++) {
            saved[i] = main.entryAt(i).enabled;
            main.entryAt(i).enabled = true;
        }
        try {
            final DebugLineRules rules = DebugLineRules.compile(main, null);
            for (String text : DebugLineRules.DEFAULTS) {
                final String prefix = text.substring(text.indexOf('"') + 1, text.indexOf('"', text.indexOf('"') + 1));
                final DebugLineRules.Rule rule = rules.match(text.split(" ")[1].equals("left") ? LEFT : RIGHT, prefix + "1");
                assertNotNull(rule, text);
                assertEquals(text.startsWith("fact"), rule.group() != null, text);
                assertNotNull(rule.when(), text);
            }
        } finally {
            for (int i = 0; i < saved.length; i++) main.entryAt(i).enabled = saved[i];
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { DebugGroupHandle.LEFT, DebugGroupHandle.RIGHT })
    void dropAndFactMatchOnlyTheirSideAndCase(int side) {
        final String word = side == LEFT ? "left" : "right";
        final DebugLineRules rules = compile("drop " + word + " \"Spam: \"", "fact " + word + " \"Battery: \" group=angelica:memory");
        assertNull(rules.match(side, "Spam: 1").group());
        assertNull(rules.match(1 - side, "Spam: 1"));
        assertSame(memoryGroup, rules.match(side, "Battery: 80%").group());
        assertNull(rules.match(1 - side, "Battery: 80%"));
        assertNull(rules.match(side, "battery: 80%"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "\"Battery: \"                    | Battery:   80%        | Battery    | 11",
        "\"NoColon\"                      | NoColon 1             | NoColon    | 8",
        "\"[JM] Waypoints: \" name=\"Way points\" | [JM] Waypoints: 3 | Way points | 16",
        "\"; \" name=Semi                 | ; 1                   | Semi       | 2",
        "\"&b[JM] &r\" name=JM            | &b[J&lM] &rwaypoints  | JM         | 9" })
    void factNameAndPayloadIgnoreColorCodes(String rule, String line, String name, int payloadStart) {
        final String text = line.replace('&', '\u00a7');
        final DebugLineRules.Rule matched = compile(("fact left " + rule + " group=angelica:position").replace('&', '\u00a7')).match(LEFT, text);
        assertEquals(name, matched.name());
        assertEquals(payloadStart, matched.payloadStart(text));
    }

    @Test
    void whenGatesTheRuleAndAnUnsatisfiedRuleFallsThrough() {
        final DebugLineRules rules = compile("drop right \"IntCache: \" when=minecraft:system_specs");
        assertNull(rules.match(RIGHT, "IntCache: 1").group());
        specs.enabled = false;
        assertSame(memoryGroup, rules.match(RIGHT, "IntCache: 1").group());
        memory.enabled = false;
        assertNull(rules.match(RIGHT, "IntCache: 1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "erase left \"Bad: \"", "drop any \"Bad: \"", "drop left", "drop left \"Bad: ", "drop left \"\"", "drop left \"\u00a7a\u00a7r\"",
        "drop left \"Bad: 0123456789012345678901234567890123456789012345678901234567890123456789\"", "drop left \"Bad: \" color=red",
        "drop left \"Bad: \" when=minecraft:missing", "drop left \"Bad: \" group=angelica:memory", "drop left \"Bad: \" name=x",
        "fact left \"Bad: \"", "fact left \"Bad: \" group=angelica:missing", "fact left \"Bad: \" group=angelica:memory name=\"\"",
        "fact left \": \" group=angelica:memory" })
    void invalidRuleIsSkippedWithoutAffectingOthers(String invalid) {
        final DebugLineRules rules = compile("drop left \"Before: \"", invalid, null, "drop left \"After: \"");
        assertNull(rules.match(LEFT, "Bad: 1"));
        assertNull(rules.match(LEFT, ": 1"));
        assertNotNull(rules.match(LEFT, "Before: 1"));
        assertNotNull(rules.match(LEFT, "After: 1"));
    }

    @Test
    void configRulesWinOverDefaultsAndEarlierRulesWin() {
        assertSame(memoryGroup, compile().match(RIGHT, "IntCache: 1").group());
        final DebugLineRules rules = compile("drop right \"IntCache: \"", "fact right \"Allocation rate: \" group=angelica:memory", "drop right \"Allocation\"");
        assertNull(rules.match(RIGHT, "IntCache: 1").group());
        assertSame(memoryGroup, rules.match(RIGHT, "Allocation rate: 1").group());
        assertNull(rules.match(RIGHT, "Allocations: 1").group());
    }
}
