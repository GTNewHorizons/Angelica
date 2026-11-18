package com.gtnewhorizons.angelica.debug.screen;

import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DebugContextImplTest {

    private static final long MS = 1_000_000L;

    private final Minecraft mc = mock(Minecraft.class);
    private final DebugContextImpl context = new DebugContextImpl();

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "steady 2 Hz,          1000:5:true 1050:5:false 1499:5:false 1500:5:true 1999:5:false 2000:5:true",
        "entry list change,    1000:5:true 1050:6:true 1100:6:false 1549:6:false 1550:6:true" })
    void samplesAreDueTwicePerSecondOrWhenTheEntryListChanges(String name, String steps) {
        for (final String step : steps.split(" ")) {
            final String[] parts = step.split(":");
            context.prepare(mc, Long.parseLong(parts[0]) * MS, Long.parseLong(parts[1]));
            assertEquals(Boolean.parseBoolean(parts[2]), context.isSampleDue(), step);
        }
    }

    @Test
    void sampleIsDueForTheDisplayInWhichAnEntryFirstAsks() {
        final DebugRegistry registry = new DebugRegistry();
        final DebugEntryDef early = registry.registerEntry(TestRegistry.text("test:early"));
        final DebugEntryDef late = registry.registerEntry(TestRegistry.text("test:late"));
        context.prepare(mc, 1000L * MS, 5L);
        context.beginEntry(early);
        assertTrue(context.isSampleDue());
        context.prepare(mc, 1050L * MS, 5L);
        context.beginEntry(early);
        assertFalse(context.isSampleDue());
        context.beginEntry(late);
        assertTrue(context.isSampleDue());
        assertTrue(context.isSampleDue());
        context.beginEntry(early);
        assertFalse(context.isSampleDue());
        context.prepare(mc, 1100L * MS, 5L);
        context.beginEntry(late);
        assertFalse(context.isSampleDue());
    }
}
