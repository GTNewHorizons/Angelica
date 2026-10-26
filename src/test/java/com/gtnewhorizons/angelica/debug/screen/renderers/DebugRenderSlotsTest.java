package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import com.gtnewhorizons.angelica.debug.screen.DebugEntryDef;
import com.gtnewhorizons.angelica.debug.screen.DebugRegistry;
import com.gtnewhorizons.angelica.debug.screen.DebugScreenEntries;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugRenderSlotsTest {

    private static final long MILLI = 1_000_000L;
    private static final int[] KINDS = { DebugScreenEntries.KIND_CHUNK_BORDERS, DebugScreenEntries.KIND_VISUALIZE_HEIGHTMAP, DebugScreenEntries.KIND_VISUALIZE_SOLID_FACES };

    private static final class FakeRenderer implements DebugWorldRenderer {

        int rebuilds;
        int min = 250;
        int fallback = 2000;
        int content = -1;
        boolean watches;
        boolean fails;
        Granularity granularity = Granularity.BLOCK;

        @Override
        public void rebuild(ScanState scan, DebugMeshData mesh) {
            rebuilds++;
            if (fails) throw new IllegalStateException("rebuild failed");
            mesh.line(DebugMeshData.LineWidth.THIN, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, content);
            if (watches) scan.watch(0, 0, 0, 15, 15, 15);
        }

        @Override
        public Granularity viewerGranularity() {
            return granularity;
        }

        @Override
        public int minIntervalMillis() {
            return min;
        }

        @Override
        public int fallbackIntervalMillis() {
            return fallback;
        }
    }

    private static final Class<?>[] REGISTER_BUILTIN = { DebugEntrySpec.class, int.class, boolean.class };

    private final FakeRenderer[] renderers = { new FakeRenderer(), new FakeRenderer(), new FakeRenderer() };
    private final DebugEntryDef[] defs = new DebugEntryDef[3];
    private DebugRenderSlots slots;
    private int uploads;

    private DebugRenderSlots slots() {
        final DebugRegistry registry = new DebugRegistry();
        registry.registerEntry(new DebugEntrySpec("test:gate", DebugEntryCategory.RENDERER).defaults(DebugScreenEntryStatus.NEVER, DebugScreenEntryStatus.NEVER));
        for (int i = 0; i < 3; i++) {
            final DebugEntrySpec spec = new DebugEntrySpec("test:renderer_" + i, DebugEntryCategory.RENDERER).defaults(DebugScreenEntryStatus.NEVER, DebugScreenEntryStatus.NEVER);
            defs[i] = Reflect.invoke(registry, "registerBuiltin", REGISTER_BUILTIN, spec, KINDS[i], false);
        }
        registry.freeze();
        slots = new DebugRenderSlots(registry);
        final DebugWorldRenderer[] installed = Reflect.get(slots, "renderers");
        for (int i = 0; i < 3; i++) installed[defs[i].rendererIndex] = renderers[i];
        return slots;
    }

    private int update(DebugRenderSlots s, long now, int x, int y, int z) {
        final int slot = s.update(now, x, y, z);
        if (slot >= 0 && s.takeUpload(slot)) uploads++;
        return slot;
    }

    private int slot(int i) {
        return defs[i].rendererIndex;
    }

    @AfterEach
    void release() {
        if (slots == null) return;
        for (int i = 0; i < slots.count; i++) slots.setEnabled(i, false);
        slots.releaseIfIdle();
    }

    @Test
    void disableMarksTheSlotForDiscardAndLiveEndsWhenIdle() {
        final DebugRenderSlots s = slots();
        s.setEnabled(slot(0), true);
        assertEquals(slot(0), s.update(0L, 0, 0, 0));
        s.setEnabled(slot(0), false);
        assertTrue(s.isPendingDiscard(slot(0)));
        assertTrue(s.isLive());
        s.releaseIfIdle();
        assertFalse(s.isLive());
    }

    @Test
    void dirtySlotWaitsForItsMinimumInterval() {
        final DebugRenderSlots s = slots();
        renderers[0].watches = true;
        s.setEnabled(slot(0), true);
        assertEquals(slot(0), s.update(0L, 0, 0, 0));
        s.changes.markBlockForUpdate(1, 1, 1);
        assertEquals(-1, s.update(250 * MILLI - 1, 0, 0, 0));
        assertEquals(slot(0), s.update(250 * MILLI, 0, 0, 0));
        assertFalse(s.changes.isDirty(slot(0)));
    }

    @Test
    void fallbackIntervalFiresAndNegativeFallbackNeverDoes() {
        final DebugRenderSlots s = slots();
        renderers[1].fallback = -1;
        s.setEnabled(slot(0), true);
        s.setEnabled(slot(1), true);
        assertEquals(slot(0), s.update(0L, 0, 0, 0));
        assertEquals(slot(1), s.update(0L, 0, 0, 0));
        assertEquals(-1, s.update(2000 * MILLI - 1, 0, 0, 0));
        assertEquals(slot(0), s.update(2000 * MILLI, 0, 0, 0));
        assertEquals(slot(0), s.update(3_000_000 * MILLI, 0, 0, 0));
        assertEquals(1, renderers[1].rebuilds);
    }

    @Test
    void viewerMovementTriggersByGranularity() {
        final DebugRenderSlots s = slots();
        renderers[1].granularity = DebugWorldRenderer.Granularity.SECTION;
        renderers[2].granularity = DebugWorldRenderer.Granularity.CHUNK;
        for (int i = 0; i < 3; i++) {
            renderers[i].fallback = -1;
            s.setEnabled(slot(i), true);
            s.update(0L, 0, 0, 0);
        }
        assertEquals(-1, s.update(300 * MILLI, 0, 0, 0));
        assertEquals(slot(0), s.update(300 * MILLI, 1, 0, 0));
        assertEquals(-1, s.update(300 * MILLI, 1, 0, 0));
        assertEquals(slot(1), s.update(600 * MILLI, 1, 17, 0));
        assertEquals(slot(0), s.update(600 * MILLI, 1, 17, 0));
        assertEquals(-1, s.update(600 * MILLI, 1, 17, 0));
        assertEquals(slot(2), s.update(900 * MILLI, 16, 17, 0));
    }

    @Test
    void rebuildWithAnUnchangedHashDoesNotUpload() {
        final DebugRenderSlots s = slots();
        final FakeRenderer renderer = renderers[0];
        renderer.min = 0;
        renderer.fallback = 0;
        s.setEnabled(slot(0), true);
        update(s, 0L, 0, 0, 0);
        update(s, 1L, 0, 0, 0);
        assertEquals(2, renderer.rebuilds);
        assertEquals(1, uploads);
        renderer.content = 0xFF00FF00;
        update(s, 2L, 0, 0, 0);
        assertEquals(2, uploads);
    }

    @Test
    void blockStepInsideASectionWithIdenticalEmissionDoesNotUpload() {
        final DebugRenderSlots s = slots();
        renderers[0].fallback = -1;
        s.setEnabled(slot(0), true);
        assertEquals(slot(0), update(s, 0L, 3, 70, 5));
        assertEquals(slot(0), update(s, 300 * MILLI, 4, 70, 5));
        assertEquals(slot(0), update(s, 600 * MILLI, 4, 71, 6));
        assertEquals(3, renderers[0].rebuilds);
        assertEquals(1, uploads);
        assertEquals(slot(0), update(s, 900 * MILLI, 16, 71, 6));
        assertEquals(2, uploads);
    }

    @Test
    void throwingRendererIsDisabledAndTheOthersKeepRunning() {
        final DebugRenderSlots s = slots();
        renderers[0].fails = true;
        s.setEnabled(slot(0), true);
        s.setEnabled(slot(1), true);
        assertEquals(slot(0), s.update(0L, 0, 0, 0));
        assertEquals(1, s.enabledCount());
        assertTrue(defs[0].isFailed());
        assertFalse(s.takeUpload(slot(0)));
        assertEquals(slot(1), s.update(1L, 0, 0, 0));
        assertFalse(defs[1].isFailed());
    }

    @Test
    void worldResetDiscardsMeshesAndReuploadsOnNextUpdate() {
        final DebugRenderSlots s = slots();
        s.setEnabled(slot(0), true);
        update(s, 0L, 0, 0, 0);
        s.resetWorld();
        assertFalse(s.isDrawable(slot(0)));
        assertEquals(1, s.enabledCount());
        assertTrue(s.isPendingDiscard(slot(0)));
        s.clearPendingDiscard(slot(0));
        assertEquals(slot(0), update(s, 1L, 0, 0, 0));
        assertEquals(2, uploads);
    }
}
