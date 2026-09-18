package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;

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

    private BatchEligibility() {}

    public static boolean begin(byte state, long drawCount) {
        if (depth++ > 0) return allowed;
        drawsAtStart = drawCount;
        expectedDraws = 0L;
        foreignDraws = 0L;
        parts = 0;
        allowed = state == SAFE;
        return allowed;
    }

    public static byte end(byte state, long drawCount) {
        if (--depth > 0) return state;
        allowed = false;
        GLSMHooks.resolvePendingProgram();
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
        GLStateManager.warnOnce("tesr-mixed:" + name, "{} draws its own geometry alongside model parts ({} foreign draws) - excluding it from model part batching", name, foreignDraws);
    }
}
