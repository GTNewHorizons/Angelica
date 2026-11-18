package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugGroupSpec;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugRegistryTest {

    private final DebugRegistry registry = new DebugRegistry();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "noseparator", ":path", "ns:", "Ns:path", "ns:Path", "ns:pa th", "ns/x:path", "ns:a:b", "ns:p\u00e4th" })
    void badIdsAreRejected(String id) {
        assertThrows(IllegalArgumentException.class, () -> registry.registerEntry(TestRegistry.text(id)));
    }

    @Test
    void malformedRegistrationsAreRejected() {
        registry.registerEntry(TestRegistry.text("some-mod.x:dir/entry_1.b"));
        registry.registerGroup(new DebugGroupSpec("test:g"));
        assertThrows(IllegalArgumentException.class, () -> registry.registerEntry(TestRegistry.renderer("some-mod.x:dir/entry_1.b")));
        assertThrows(IllegalArgumentException.class, () -> registry.registerGroup(new DebugGroupSpec("test:g")));
        assertThrows(IllegalArgumentException.class, () -> registry.registerEntry(TestRegistry.text("test:a").defaults(null, DebugScreenEntryStatus.NEVER)));
        assertThrows(IllegalArgumentException.class, () -> registry.registerEntry(new DebugEntrySpec("test:b", null)));
        assertThrows(IllegalArgumentException.class, () -> registry.registerGroup(new DebugGroupSpec("test:h").side(2)));
        assertThrows(IllegalArgumentException.class, () -> registry.registerEntry(TestRegistry.renderer("test:c").text(entry -> null)));
    }

    @Test
    void registrationAfterFreezeThrows() {
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.registerEntry(TestRegistry.text("test:a")));
        assertThrows(IllegalStateException.class, () -> registry.registerBuiltin(TestRegistry.text("test:b"), 1, false));
        assertThrows(IllegalStateException.class, () -> registry.registerGroup(new DebugGroupSpec("test:g")));
    }

    @Test
    void groupsSortBySideThenOrderThenRegistration() {
        final DebugGroup rightLate = TestRegistry.group(registry, "test:right_late", DebugGroupHandle.RIGHT, 900);
        final DebugGroup leftDefault = registry.registerGroup(new DebugGroupSpec("test:left_default"));
        final DebugGroup leftFirst = TestRegistry.group(registry, "test:left_first", DebugGroupHandle.LEFT, 0);
        final DebugGroup rightEarly = TestRegistry.group(registry, "test:right_early", DebugGroupHandle.RIGHT, 100);
        final DebugGroup leftDefaultSecond = TestRegistry.group(registry, "test:left_default_second", DebugGroupHandle.LEFT, DebugGroupSpec.DEFAULT_ORDER);
        registry.freeze();
        final DebugGroup[] sorted = new DebugGroup[registry.groupCount()];
        for (int i = 0; i < sorted.length; i++) sorted[i] = registry.groupAt(i);
        assertArrayEquals(new DebugGroup[] { leftFirst, leftDefault, leftDefaultSecond, rightEarly, rightLate }, sorted);
    }

    @Test
    void flatListHoldsThirdPartyAndFlatBuiltinTextEntries() {
        final DebugEntryDef thirdParty = registry.registerEntry(TestRegistry.text("test:third").text(handle -> (displayer, context) -> {}));
        registry.registerEntry(TestRegistry.text("test:gate"));
        registry.registerBuiltin(TestRegistry.text("test:grouped").text(handle -> (displayer, context) -> {}), 7, false);
        final DebugEntryDef builtinFlat = registry.registerBuiltin(TestRegistry.text("test:flat").text(handle -> (displayer, context) -> {}), 8, true);
        registry.registerEntry(TestRegistry.renderer("test:renderer"));
        registry.freeze();
        assertEquals(2, registry.flatCount());
        assertSame(thirdParty, registry.flatAt(0));
        assertSame(builtinFlat, registry.flatAt(1));
    }

    @Test
    void failuresAreLatchedOncePerEntry() {
        final DebugEntryDef def = registry.registerEntry(TestRegistry.text("test:a"));
        assertFalse(registry.consumeFailures());
        registry.fail(def, new IllegalStateException("boom"));
        assertTrue(def.isFailed());
        assertTrue(registry.consumeFailures());
        assertFalse(registry.consumeFailures());
        registry.fail(def, new IllegalStateException("again"));
        assertFalse(registry.consumeFailures());
    }
}
