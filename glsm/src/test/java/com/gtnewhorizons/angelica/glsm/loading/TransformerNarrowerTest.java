package com.gtnewhorizons.angelica.glsm.loading;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformerNarrowerTest {

    private static final String LEGENDS_BROAD = "com.tihyo.legends.management.asm";
    private static final String LEGENDS_HOOK = LEGENDS_BROAD + ".ASMClientMethods";

    private static Set<String> exceptionsWith(String... entries) {
        return new HashSet<>(Set.of(entries));
    }

    private static boolean isExcluded(Set<String> exceptions, String className) {
        return exceptions.stream().anyMatch(className::startsWith);
    }

    private static int narrow(Set<String> exceptions, Map<String, Object> blackboard) {
        return TransformerNarrower.narrow(exceptions, blackboard, "angelica", "Angelica", EcosystemNarrowRules.ALL);
    }

    @Test
    void legendsHooksBecomeTransformableButTransformersDoNot() {
        final Set<String> exceptions = exceptionsWith(LEGENDS_BROAD);
        assertEquals(1, narrow(exceptions, new HashMap<>()));

        assertFalse(isExcluded(exceptions, LEGENDS_HOOK));
        assertFalse(isExcluded(exceptions, LEGENDS_BROAD + ".ASMMethods"));

        assertTrue(isExcluded(exceptions, LEGENDS_BROAD + ".LegendsLoadingPlugin"));
        assertTrue(isExcluded(exceptions, LEGENDS_BROAD + ".AbstractTransformer"));
        assertTrue(isExcluded(exceptions, LEGENDS_BROAD + ".ASMHelper"));
        assertTrue(isExcluded(exceptions, LEGENDS_BROAD + ".TransformerRenderPlayer"));
        assertTrue(isExcluded(exceptions, LEGENDS_BROAD + ".TransformerEntityRenderer"));
        assertTrue(isExcluded(exceptions, LEGENDS_BROAD + ".TransformerMethodProcess"));
        assertTrue(isExcluded(exceptions, LEGENDS_BROAD + ".TransformerGuiInventory"));
    }

    @Test
    void disabledRuleLeavesBroadExclusionIntact() {
        final Set<String> exceptions = exceptionsWith(LEGENDS_BROAD);
        final Map<String, Object> blackboard = new HashMap<>();
        blackboard.put("angelica.narrow.LegendsMod", Boolean.FALSE);

        assertEquals(0, narrow(exceptions, blackboard));
        assertTrue(exceptions.contains(LEGENDS_BROAD));
        assertTrue(isExcluded(exceptions, LEGENDS_HOOK));
    }

    @Test
    void sharedXaerosKeyGovernsBothExclusions() {
        final Set<String> enabled = exceptionsWith("xaero.common.core", "xaero.map.core");
        assertEquals(2, narrow(enabled, new HashMap<>()));
        assertFalse(isExcluded(enabled, "xaero.common.gui.GuiMap"));
        assertFalse(isExcluded(enabled, "xaero.map.gui.GuiMap"));
        assertTrue(isExcluded(enabled, "xaero.common.core.transformer.Foo"));
        assertTrue(isExcluded(enabled, "xaero.map.core.transformer.Foo"));

        final Set<String> disabled = exceptionsWith("xaero.common.core", "xaero.map.core");
        final Map<String, Object> blackboard = new HashMap<>();
        blackboard.put("angelica.narrow.Xaeros", Boolean.FALSE);
        assertEquals(0, narrow(disabled, blackboard));
        assertTrue(disabled.contains("xaero.common.core"));
        assertTrue(disabled.contains("xaero.map.core"));
    }

    @Test
    void narrowingIsIdempotent() {
        final Set<String> exceptions = exceptionsWith(LEGENDS_BROAD);
        assertEquals(1, narrow(exceptions, new HashMap<>()));

        final Set<String> afterFirst = new HashSet<>(exceptions);
        assertEquals(0, narrow(exceptions, new HashMap<>()));
        assertEquals(afterFirst, exceptions);
    }

    @Test
    void absentBroadExclusionIsNoOp() {
        final Set<String> exceptions = exceptionsWith("some.unrelated.package");
        assertEquals(0, narrow(exceptions, new HashMap<>()));
        assertEquals(Set.of("some.unrelated.package"), exceptions);
    }
}
