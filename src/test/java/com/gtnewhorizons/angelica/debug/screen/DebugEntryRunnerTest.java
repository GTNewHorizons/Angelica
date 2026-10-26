package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DebugEntryRunnerTest {

    private final DebugRegistry registry = new DebugRegistry();
    private final DebugContext context = mock(DebugContext.class);
    private final int[] displayed = new int[5];
    private final int[] toggled = new int[2];
    private DebugEntryDef[] text;
    private DebugEntryDef throwingToggle;
    private DebugEntryDef goodToggle;
    private DebugScreenEntryList list;
    private DebugScreenEntryList savedEntries;
    private Minecraft mc;

    @BeforeEach
    void setUp() {
        text = new DebugEntryDef[] {
            registry.registerEntry(TestRegistry.text("test:good").text(handle -> (displayer, ctx) -> displayed[0]++)),
            registry.registerEntry(TestRegistry.text("test:throwing_display").text(handle -> (displayer, ctx) -> {
                displayed[1]++;
                throw new IllegalStateException("display");
            })),
            registry.registerEntry(TestRegistry.text("test:throwing_factory").text(handle -> {
                displayed[2]++;
                throw new IllegalStateException("factory");
            })),
            registry.registerEntry(TestRegistry.text("test:linkage").text(handle -> (displayer, ctx) -> {
                displayed[3]++;
                throw new NoClassDefFoundError("gone/Clazz");
            })),
            registry.registerEntry(TestRegistry.text("test:last").text(handle -> (displayer, ctx) -> displayed[4]++)) };
        throwingToggle = registry.registerEntry(TestRegistry.renderer("test:throwing_toggle").defaults(DebugScreenEntryStatus.IN_OVERLAY, DebugScreenEntryStatus.NEVER).toggle((handle, enabled) -> {
            toggled[0]++;
            throw new IllegalStateException("toggle");
        }));
        goodToggle = registry.registerEntry(TestRegistry.renderer("test:good_toggle").defaults(DebugScreenEntryStatus.IN_OVERLAY, DebugScreenEntryStatus.NEVER).toggle((handle, enabled) -> toggled[1]++));
        registry.freeze();
        list = new DebugScreenEntryList(registry);
        savedEntries = DebugScreen.ENTRIES;
        DebugScreen.ENTRIES = list;
        mc = Reflect.allocate(Minecraft.class);
        mc.theWorld = mock(WorldClient.class);
        final EntityClientPlayerMP player = Reflect.allocate(EntityClientPlayerMP.class);
        Reflect.set(player, "boundingBox", AxisAlignedBB.getBoundingBox(0, 0, 0, 1, 1, 1));
        mc.thePlayer = player;
    }

    @AfterEach
    void tearDown() {
        DebugScreen.ENTRIES = savedEntries;
    }

    private void runEnabledText() {
        DebugScreen.runEntries(null, mc, 0L, null, false);
    }

    @Test
    void throwingEntriesDisableOnlyThemselves() {
        list.setOverlayVisible(true);
        runEnabledText();
        assertArrayEquals(new int[] { 1, 1, 1, 1, 1 }, displayed);
        for (int i = 0; i < text.length; i++) assertEquals(i >= 1 && i <= 3, text[i].isFailed(), text[i].id);
        list.refresh();
        runEnabledText();
        assertArrayEquals(new int[] { 2, 1, 1, 1, 2 }, displayed);
        for (int i = 0; i < text.length; i++) assertEquals(i == 0 || i == 4, text[i].isEnabled(), text[i].id);
    }

    @Test
    void throwingToggleDisablesOnlyItsEntry() {
        list.setOverlayVisible(true);
        assertTrue(throwingToggle.isFailed());
        assertTrue(goodToggle.isEnabled());
        list.refresh();
        assertFalse(throwingToggle.isEnabled());
        list.setOverlayVisible(false);
        assertArrayEquals(new int[] { 2, 2 }, toggled);
    }

    @Test
    void nullWorldOrPlayerCallsNothing() {
        list.setOverlayVisible(true);
        final WorldClient world = mc.theWorld;
        mc.theWorld = null;
        runEnabledText();
        mc.theWorld = world;
        mc.thePlayer = null;
        runEnabledText();
        assertArrayEquals(new int[5], displayed);
    }

    @Test
    void alwaysOnOnlyRunsOnlyAlwaysOnText() {
        list.setOverlayVisible(true);
        list.overrideForSession(text[0], DebugScreenEntryStatus.ALWAYS_ON);
        DebugScreen.runEntries(null, mc, 0L, null, true);
        assertArrayEquals(new int[] { 1, 0, 0, 0, 0 }, displayed);
    }

    @Test
    void nullFactNameOrGroupFailsOnlyThatEntry() {
        when(context.world()).thenReturn(mock(World.class));
        when(context.player()).thenReturn(mock(EntityClientPlayerMP.class));
        final DebugRegistry other = new DebugRegistry();
        final DebugGroup group = TestRegistry.group(other, "test:group", DebugGroupHandle.LEFT, 100);
        final DebugEntryDef nullName = other.registerEntry(TestRegistry.text("test:null_name").text(handle -> (displayer, ctx) -> displayer.fact(group, null).value(1)));
        final DebugEntryDef nullGroup = other.registerEntry(TestRegistry.text("test:null_group").text(handle -> (displayer, ctx) -> displayer.fact(null, "Lost").value(1)));
        final DebugEntryDef after = other.registerEntry(TestRegistry.text("test:after").text(handle -> (displayer, ctx) -> displayer.fact(group, "After").value(2)));
        other.freeze();
        final GroupedDebugDisplayer grouped = new GroupedDebugDisplayer(other);
        final FlatDebugDisplayer flat = new FlatDebugDisplayer(other);
        flat.beginLines();
        for (int i = 0, n = other.entryCount(); i < n; i++) {
            DebugScreen.display(other.entryAt(i), grouped, context);
            DebugScreen.display(other.entryAt(i), flat, context);
        }
        flat.endLines();
        assertTrue(nullName.isFailed());
        assertTrue(nullGroup.isFailed());
        assertFalse(after.isFailed());
        final DebugGroupContents contents = grouped.contents(group.index());
        assertEquals(1, contents.factCount());
        assertEquals("After", contents.fact(0).name);
        assertEquals(1, flat.lineCount(DebugGroupHandle.LEFT));
        assertEquals("After: \u00a7f2\u00a7r", flat.lineAt(DebugGroupHandle.LEFT, 0));
    }
}
