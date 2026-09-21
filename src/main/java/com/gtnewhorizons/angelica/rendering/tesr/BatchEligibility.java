package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;

import java.util.ArrayList;

/** Excludes renderers that mix batched model parts with their own draws, which reorders their geometry. */
public final class BatchEligibility {

    public static final byte UNKNOWN = 0;
    public static final byte SAFE = 1;
    public static final byte DENIED = 2;

    private static int depth;
    private static boolean allowed;
    private static long drawsAtStart;
    private static long expectedDraws;
    private static long foreignDraws;
    private static long bracketDrawsAtStart;
    private static long bracketExpectedAtStart;
    private static int bracketDepth;
    private static int parts;
    private static boolean uncapturedState;

    private static final ArrayList<SavedScope> isolatedScopes = new ArrayList<>();
    private static int isolatedDepth;

    private static final class SavedScope {
        int depth, bracketDepth, parts;
        boolean allowed, uncapturedState;
        long drawsAtStart, expectedDraws, foreignDraws, bracketDrawsAtStart, bracketExpectedAtStart, isolatedDrawsAtStart;

        void save(long drawCount) {
            depth = BatchEligibility.depth;
            allowed = BatchEligibility.allowed;
            uncapturedState = BatchEligibility.uncapturedState;
            drawsAtStart = BatchEligibility.drawsAtStart;
            expectedDraws = BatchEligibility.expectedDraws;
            foreignDraws = BatchEligibility.foreignDraws;
            bracketDrawsAtStart = BatchEligibility.bracketDrawsAtStart;
            bracketExpectedAtStart = BatchEligibility.bracketExpectedAtStart;
            bracketDepth = BatchEligibility.bracketDepth;
            parts = BatchEligibility.parts;
            isolatedDrawsAtStart = drawCount;
        }

        void restore(long drawCount) {
            BatchEligibility.depth = depth;
            BatchEligibility.allowed = allowed;
            BatchEligibility.uncapturedState = uncapturedState;
            BatchEligibility.drawsAtStart = drawsAtStart;
            BatchEligibility.expectedDraws = expectedDraws + drawCount - isolatedDrawsAtStart;
            BatchEligibility.foreignDraws = foreignDraws;
            BatchEligibility.bracketDrawsAtStart = bracketDrawsAtStart;
            BatchEligibility.bracketExpectedAtStart = bracketExpectedAtStart;
            BatchEligibility.bracketDepth = bracketDepth;
            BatchEligibility.parts = parts;
        }
    }

    private BatchEligibility() {}

    public static void beginIsolated(byte state, long drawCount) {
        if (isolatedDepth == isolatedScopes.size()) isolatedScopes.add(new SavedScope());
        isolatedScopes.get(isolatedDepth++).save(drawCount);
        depth = 0;
        bracketDepth = 0;
        begin(state, drawCount);
    }

    public static byte endIsolated(byte state, long drawCount) {
        try {
            return end(state, drawCount);
        } finally {
            isolatedScopes.get(--isolatedDepth).restore(drawCount);
        }
    }

    public static boolean begin(byte state, long drawCount) {
        if (depth++ > 0) return allowed;
        drawsAtStart = drawCount;
        expectedDraws = 0L;
        foreignDraws = 0L;
        parts = 0;
        uncapturedState = false;
        allowed = state == SAFE;
        return allowed;
    }

    public static byte end(byte state, long drawCount) {
        if (--depth > 0) return state;
        allowed = false;
        GLSMHooks.resolvePendingProgram();
        if (uncapturedState) return DENIED;
        if (parts == 0) return state;
        final long foreign = (drawCount - drawsAtStart) - expectedDraws;
        if (foreign > 0L) {
            foreignDraws = foreign;
            return DENIED;
        }
        return state == UNKNOWN ? SAFE : state;
    }

    public static boolean batchingAllowed() {
        return allowed;
    }

    static boolean insideRenderer() { return depth != 0; }

    static void onUncapturedState() {
        if (depth == 0) return;
        uncapturedState = true;
        allowed = false;
    }

    static void onBatchFlushed(long draws) {
        if (depth != 0) expectedDraws += draws;
    }

    public static void onPartQueued() {
        parts++;
    }

    public static void onPartFallback(long drawsBefore, long drawsNow) {
        parts++;
        if (!allowed) {
            expectedDraws += drawsNow - drawsBefore;
        }
    }

    public static void beginExpectedDraws(long drawCount) {
        if (bracketDepth++ > 0) return;
        bracketDrawsAtStart = drawCount;
        bracketExpectedAtStart = expectedDraws;
    }

    public static void endExpectedDraws(long drawCount) {
        if (--bracketDepth > 0) return;
        final long alreadyExpected = expectedDraws - bracketExpectedAtStart;
        expectedDraws += (drawCount - bracketDrawsAtStart) - alreadyExpected;
    }

    public static void onStateChange(Object renderer, byte state) {
        if (state != DENIED) return;
        final String name = renderer.getClass().getName();
        if (uncapturedState) {
            GLStateManager.warnOnce("tesr-state:" + name, "{} changes state outside the batch format - keeping its model parts immediate", name);
            return;
        }
        GLStateManager.warnOnce("tesr-mixed:" + name, "{} draws its own geometry alongside model parts ({} foreign draws) - excluding it from model part batching", name, foreignDraws);
    }
}
