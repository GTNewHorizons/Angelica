package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.debug.screen.DebugEntryDef;
import com.gtnewhorizons.angelica.debug.screen.DebugRegistry;
import com.gtnewhorizons.angelica.debug.screen.DebugScreen;

final class DebugRenderSlots {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    final int count;
    final DebugWorldChanges changes;
    final ScanState scan;
    private final DebugEntryDef[] defs;
    private final DebugWorldRenderer[] renderers;
    private final boolean[] enabled;
    private final boolean[] fresh;
    private final boolean[] drawable;
    private final boolean[] built;
    private final boolean[] pendingDiscard;
    private final boolean[] uploadPending;
    private final DebugWorldRenderer.Granularity[] granularity;
    private final int[] keyX;
    private final int[] keyY;
    private final int[] keyZ;
    private final long[] minNanos;
    private final long[] fallbackNanos;
    private final long[] lastBuild;
    private final long[] hash;
    final DebugMeshData build = new DebugMeshData();
    private int enabledCount;
    private boolean live;

    DebugRenderSlots(DebugRegistry registry) {
        final int n = registry.rendererCount();
        this.count = n;
        this.changes = new DebugWorldChanges(n);
        this.scan = new ScanState(changes);
        this.defs = new DebugEntryDef[n];
        this.renderers = new DebugWorldRenderer[n];
        this.enabled = new boolean[n];
        this.fresh = new boolean[n];
        this.drawable = new boolean[n];
        this.built = new boolean[n];
        this.pendingDiscard = new boolean[n];
        this.uploadPending = new boolean[n];
        this.granularity = new DebugWorldRenderer.Granularity[n];
        this.keyX = new int[n];
        this.keyY = new int[n];
        this.keyZ = new int[n];
        this.minNanos = new long[n];
        this.fallbackNanos = new long[n];
        this.lastBuild = new long[n];
        this.hash = new long[n];
        for (int i = 0; i < n; i++) {
            final DebugEntryDef def = registry.rendererAt(i);
            if (DebugRenderers.isWorldRenderer(def)) defs[i] = def;
        }
    }

    static boolean isDue(long ageNanos, long minNanos, long fallbackNanos, boolean viewerMoved, boolean dirty) {
        if (ageNanos < minNanos) return false;
        return viewerMoved || dirty || fallbackNanos >= 0L && ageNanos >= fallbackNanos;
    }

    static int key(DebugWorldRenderer.Granularity granularity, int coordinate) {
        return granularity == DebugWorldRenderer.Granularity.BLOCK ? coordinate : coordinate >> 4;
    }

    static int keyY(DebugWorldRenderer.Granularity granularity, int y) {
        return granularity == DebugWorldRenderer.Granularity.CHUNK ? 0 : key(granularity, y);
    }

    static int anchor(int coordinate) {
        return coordinate >> 4 << 4;
    }

    static int anchorY(DebugWorldRenderer.Granularity granularity, int y) {
        return granularity == DebugWorldRenderer.Granularity.CHUNK ? 0 : anchor(y);
    }

    boolean isLive() {
        return live;
    }

    int enabledCount() {
        return enabledCount;
    }

    boolean isDrawable(int slot) {
        return drawable[slot];
    }

    void setEnabled(int slot, boolean on) {
        final DebugEntryDef def = defs[slot];
        if (def == null || enabled[slot] == on) return;
        if (!on) {
            disable(slot);
            return;
        }
        DebugWorldRenderer renderer = renderers[slot];
        if (renderer == null) {
            renderer = DebugRenderers.create(def);
            renderers[slot] = renderer;
        }
        final int fallback = renderer.fallbackIntervalMillis();
        granularity[slot] = renderer.viewerGranularity();
        minNanos[slot] = renderer.minIntervalMillis() * NANOS_PER_MILLI;
        fallbackNanos[slot] = fallback < 0 ? -1L : fallback * NANOS_PER_MILLI;
        pendingDiscard[slot] = false;
        enabled[slot] = true;
        fresh[slot] = true;
        enabledCount++;
        live = true;
    }

    private void disable(int slot) {
        enabled[slot] = false;
        enabledCount--;
        retire(slot);
    }

    private void retire(int slot) {
        drawable[slot] = false;
        built[slot] = false;
        fresh[slot] = true;
        changes.clearBox(slot);
        uploadPending[slot] = false;
        pendingDiscard[slot] = true;
    }

    void fail(int slot, Throwable cause) {
        DebugScreen.fail(defs[slot], cause);
        if (enabled[slot]) disable(slot);
    }

    void resetWorld() {
        for (int i = 0; i < count; i++) {
            if (renderers[i] != null && (enabled[i] || built[i])) retire(i);
        }
    }

    boolean isPendingDiscard(int slot) {
        return pendingDiscard[slot];
    }

    void clearPendingDiscard(int slot) {
        pendingDiscard[slot] = false;
    }

    void releaseIfIdle() {
        if (enabledCount == 0) {
            build.free();
            live = false;
        }
    }

    boolean takeUpload(int slot) {
        final boolean pending = uploadPending[slot];
        uploadPending[slot] = false;
        return pending;
    }

    int pickDue(long now, int viewerX, int viewerY, int viewerZ) {
        int best = -1;
        long bestOverdue = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            if (!enabled[i]) continue;
            if (fresh[i]) return i;
            final DebugWorldRenderer.Granularity g = granularity[i];
            final long age = now - lastBuild[i];
            final boolean moved = keyX[i] != key(g, viewerX) || keyY[i] != keyY(g, viewerY) || keyZ[i] != key(g, viewerZ);
            if (!isDue(age, minNanos[i], fallbackNanos[i], moved, changes.isDirty(i))) continue;
            final long overdue = age - minNanos[i];
            if (overdue > bestOverdue) {
                best = i;
                bestOverdue = overdue;
            }
        }
        return best;
    }

    int update(long now, int viewerX, int viewerY, int viewerZ) {
        final int slot = pickDue(now, viewerX, viewerY, viewerZ);
        if (slot >= 0) rebuild(slot, now, viewerX, viewerY, viewerZ);
        return slot;
    }

    private void rebuild(int slot, long now, int viewerX, int viewerY, int viewerZ) {
        final DebugWorldRenderer.Granularity g = granularity[slot];
        keyX[slot] = key(g, viewerX);
        keyY[slot] = keyY(g, viewerY);
        keyZ[slot] = key(g, viewerZ);
        lastBuild[slot] = now;
        fresh[slot] = false;
        changes.clearBox(slot);
        scan.slot = slot;
        final DebugMeshData data = build;
        data.begin(anchor(viewerX), anchorY(g, viewerY), anchor(viewerZ));
        try {
            renderers[slot].rebuild(scan, data);
        } catch (RuntimeException | LinkageError e) {
            fail(slot, e);
            return;
        }
        final long h = data.hash();
        if (!built[slot] || hash[slot] != h) {
            uploadPending[slot] = true;
            hash[slot] = h;
            built[slot] = true;
        }
        drawable[slot] = true;
    }
}
