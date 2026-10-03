package com.gtnewhorizons.angelica.sdlgpu.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLVersion.SDL_VERSIONNUM;

class FenceWaitTest {

    private static final class FakeOps implements FenceWait.Ops {
        long now;
        long signalAt = Long.MAX_VALUE;
        long tick = 1_000L;
        int queries;
        int nativeWaits;
        int parks;
        long parkedNs;
        long maxPark;
        long firstParkAt = -1;

        @Override public boolean query(long dev, long fence) {
            queries++;
            return now >= signalAt;
        }

        @Override public void waitNative(long dev, long fence) {
            nativeWaits++;
            now = Math.max(now, signalAt);
        }

        @Override public long nanoTime() {
            now += tick;
            return now;
        }

        @Override public void park(long nanos) {
            assertTrue(nanos > 0, "park must never be asked for a non-positive duration");
            if (firstParkAt < 0) firstParkAt = now;
            parks++;
            parkedNs += nanos;
            maxPark = Math.max(maxPark, nanos);
            now += nanos;
        }
    }

    @Test
    void alreadySignalledReturnsWithoutWaiting() {
        final FakeOps ops = new FakeOps();
        ops.signalAt = 0;
        assertTrue(FenceWait.await(1L, 2L, FenceWait.FOREVER, true, ops));
        assertEquals(1, ops.queries);
        assertEquals(0, ops.parks);
        assertEquals(0, ops.nativeWaits);
    }

    @Test
    void zeroTimeoutIsAPureQuery() {
        final FakeOps ops = new FakeOps();
        assertFalse(FenceWait.await(1L, 2L, 0L, true, ops));
        assertEquals(1, ops.queries);
        assertEquals(0, ops.parks);
    }

    @Test
    void spinsBeforeParkingThenParksInBoundedSlices() {
        final FakeOps ops = new FakeOps();
        ops.signalAt = 500_000L;
        assertTrue(FenceWait.await(1L, 2L, FenceWait.FOREVER, true, ops));
        assertTrue(ops.firstParkAt >= FenceWait.SPIN_NS, "must spin for the first " + FenceWait.SPIN_NS + "ns, parked at " + ops.firstParkAt);
        assertTrue(ops.parks > 0);
        assertEquals(FenceWait.PARK_MAX_NS, ops.maxPark);
        assertEquals(0, ops.nativeWaits);
    }

    @Test
    void signalDuringSpinNeverParks() {
        final FakeOps ops = new FakeOps();
        ops.signalAt = 5_000L;
        assertTrue(FenceWait.await(1L, 2L, FenceWait.FOREVER, true, ops));
        assertEquals(0, ops.parks);
    }

    @Test
    void finiteTimeoutExpiresWithoutOvershootingTheDeadline() {
        final FakeOps ops = new FakeOps();
        final long timeout = 130_000L;
        final long start = ops.now;
        assertFalse(FenceWait.await(1L, 2L, timeout, true, ops));
        final long elapsed = ops.now - start;
        assertTrue(elapsed >= timeout, "returned early: " + elapsed);
        assertTrue(elapsed <= timeout + 2 * ops.tick, "last park must be clamped to the remaining time: " + elapsed);
    }

    @Test
    void finiteTimeoutPollsWhenForeverPollingIsDisabled() {
        final FakeOps ops = new FakeOps();
        ops.signalAt = 60_000L;
        assertTrue(FenceWait.await(1L, 2L, 100_000L, false, ops));
        assertEquals(0, ops.nativeWaits, "a finite timeout must poll on every backend so it can expire");
        assertTrue(ops.parks > 0);
    }

    @Test
    void interruptFlagSurvivesAPollingWait() {
        final FakeOps ops = new FakeOps();
        ops.signalAt = 500_000L;
        Thread.currentThread().interrupt();
        assertTrue(FenceWait.await(1L, 2L, FenceWait.FOREVER, true, ops));
        assertTrue(ops.parks > 0);
        assertTrue(Thread.interrupted(), "the caller's interrupt must be restored");
    }

    @Test
    void foreverWithoutPollingUsesTheNativeWait() {
        final FakeOps ops = new FakeOps();
        ops.signalAt = 1_000_000L;
        assertTrue(FenceWait.await(1L, 2L, FenceWait.FOREVER, false, ops));
        assertEquals(1, ops.nativeWaits);
        assertEquals(0, ops.parks);
    }

    @Test
    void timeoutIgnoredIsForever() {
        final FakeOps ops = new FakeOps();
        ops.signalAt = 1_000_000L;
        assertTrue(FenceWait.await(1L, 2L, -1L, false, ops));
        assertEquals(1, ops.nativeWaits);
    }

    @Test
    void pollingOnlyOnMetalWhileItsWaitSpins() {
        assertTrue(FenceWait.pollEnabled("metal", SDL_VERSIONNUM(3, 4, 12)));
        assertFalse(FenceWait.pollEnabled("metal", SDL_VERSIONNUM(3, 4, 14)));
        assertFalse(FenceWait.pollEnabled("metal", SDL_VERSIONNUM(3, 5, 0)));
        assertFalse(FenceWait.pollEnabled("vulkan", SDL_VERSIONNUM(3, 4, 12)));
        assertFalse(FenceWait.pollEnabled("direct3d12", SDL_VERSIONNUM(3, 4, 12)));
        assertFalse(FenceWait.pollEnabled(null, SDL_VERSIONNUM(3, 4, 12)));
    }
}
