package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.backend.RenderBackend.GateSample;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramePacerTest {

    private static final long CLOCK_TICK_NANOS = 1_000L;

    private static final class FakeBackend implements FramePacer.Backend {
        VSyncMode mode = VSyncMode.ON;
        long refreshPeriodNanos;
        boolean vsyncHonored = true;
        int displayGeneration;
        long gpuWaitNanos;
        long gateDurationNanos;
        long gateEndNanos;
        long now;

        int awaitPresentCalls;
        int readGateSampleCalls;
        int parkCalls;
        int pumpCalls;
        boolean awaitedBeforeRead;
        boolean readBeforePump;
        boolean parkedBeforePump;

        @Override
        public VSyncMode effectiveVSyncMode() {
            return mode;
        }

        @Override
        public long refreshPeriodNanos() {
            return refreshPeriodNanos;
        }

        @Override
        public boolean vsyncHonored() {
            return vsyncHonored;
        }

        @Override
        public int displayGeneration() {
            return displayGeneration;
        }

        @Override
        public void awaitPresent() {
            awaitPresentCalls++;
        }

        @Override
        public void readGateSample(GateSample out) {
            readGateSampleCalls++;
            awaitedBeforeRead = awaitPresentCalls > 0;
            out.durationNanos = gateDurationNanos;
            out.endNanos = gateEndNanos;
            out.gpuWaitNanos = gpuWaitNanos;
        }

        @Override
        public void pumpDisplayMessages() {
            pumpCalls++;
            readBeforePump = readGateSampleCalls > 0;
            parkedBeforePump = parkCalls > 0;
        }

        @Override
        public void parkNanos(long nanos) {
            parkCalls++;
            now += nanos;
        }

        @Override
        public long nanoTime() {
            final long t = now;
            now += CLOCK_TICK_NANOS;
            return t;
        }
    }

    @BeforeEach
    void freshPacerState() {
        final PacerSleeper previous = Reflect.getStatic(FramePacer.class, "sleeper");
        final LongSupplier clock = Reflect.get(previous, "clock");
        final LongConsumer parker = Reflect.get(previous, "parker");
        final PacerSleeper sleeper = new PacerSleeper(clock, parker, true);
        Reflect.setStatic(FramePacer.class, "sleeper", sleeper);
        Reflect.setStatic(FramePacer.class, "core", new PacerCore(clock, sleeper));
        Reflect.setStatic(FramePacer.class, "lastDisplayGeneration", 0);
        Reflect.setStatic(FramePacer.class, "warnedUncapped", false);
        Reflect.setStatic(FramePacer.class, "loggedMode", null);
        Reflect.setStatic(FramePacer.class, "loggedRefreshHz", 0);
        Reflect.setStatic(FramePacer.class, "loggedCapHz", 0);
        Reflect.setStatic(FramePacer.class, "loggedEffectiveCapHz", 0);
        Reflect.setStatic(FramePacer.class, "loggedLocked", false);
        Reflect.setStatic(FramePacer.class, "loggedProbing", false);
    }

    private static void withBackend(FramePacer.Backend fake, Runnable body) {
        final FramePacer.Backend original = Reflect.getStatic(FramePacer.class, "BACKEND");
        Reflect.setStatic(FramePacer.class, "BACKEND", fake);
        try {
            body.run();
        } finally {
            Reflect.setStatic(FramePacer.class, "BACKEND", original);
        }
    }

    @ParameterizedTest(name = "{0} refresh={1} cap={2}->{3} locked={4} probing={5}")
    @CsvSource(nullValues = "null", value = {
        "ON, 60, 0, 0, true, false, ' [vsync 60]'",
        "ON, 60, 30, 30, true, false, ' [vsync 60, cap 30 -> 30]'",
        "ON, 144, 50, 48, true, false, ' [vsync 144, cap 50 -> 48]'",
        "MAILBOX, 144, 0, 0, false, false, ' [mailbox 144]'",
        "OFF, 144, 100, 100, false, false, ' [cap 100]'",
        "ON, 60, 0, 0, false, true, ' [vsync 60, probing]'",
        "ON, 60, 0, 0, false, false, ' [vsync 60, wall clock]'",
        "OFF, 144, 0, 0, false, false, null",
    })
    void theIndicatorNamesEveryPacingState(VSyncMode mode, int refreshHz, int capHz, int effectiveCapHz, boolean locked, boolean probing, String expected) {
        assertEquals(expected, FramePacer.debugIndicator(mode, refreshHz, capHz, effectiveCapHz, locked, probing));
    }

    @ParameterizedTest(name = "{0} refresh={1} cap={2}->{3} locked={4} probing={5}")
    @CsvSource({
        "ON, 60, 0, 0, true, false, 'Frame pacing: vsync 60Hz, hardware paced'",
        "ON, 60, 0, 0, false, true, 'Frame pacing: vsync 60Hz, probing'",
        "ON, 60, 0, 0, false, false, 'Frame pacing: vsync 60Hz, driver not blocking, paced by wall clock'",
        "MAILBOX, 144, 0, 0, true, false, 'Frame pacing: mailbox 144Hz'",
        "OFF, 144, 100, 100, false, false, 'Frame pacing: vsync off, cap 100'",
        "ON, 60, 30, 30, true, false, 'Frame pacing: vsync 60Hz, hardware paced, cap 30'",
        "ON, 144, 50, 48, true, false, 'Frame pacing: vsync 144Hz, hardware paced, cap 50 -> 48'",
    })
    void thePacingLineNamesEveryPacingState(VSyncMode mode, int refreshHz, int capHz, int effectiveCapHz, boolean locked, boolean probing, String expected) {
        assertEquals(expected, FramePacer.pacingLine(mode, refreshHz, capHz, effectiveCapHz, locked, probing));
    }

    @Test
    void settersAreAppliedBeforeBeginFrame() {
        final FakeBackend backend = new FakeBackend();
        backend.mode = VSyncMode.OFF;
        backend.vsyncHonored = false;
        withBackend(backend, () -> {
            FramePacer.endFrame(60, null);
            assertTrue(FramePacer.pacedLastFrame(), "a nonzero cap must pace even under vsync off");
        });
    }

    @Test
    void awaitPresentPrecedesReadGateSampleOnlyWhenVsyncOnAndHonored() {
        final FakeBackend honored = new FakeBackend();
        honored.mode = VSyncMode.ON;
        honored.vsyncHonored = true;
        withBackend(honored, () -> {
            FramePacer.endFrame(0, null);
            assertEquals(1, honored.awaitPresentCalls);
            assertTrue(honored.awaitedBeforeRead);
        });

        final FakeBackend notHonored = new FakeBackend();
        notHonored.mode = VSyncMode.ON;
        notHonored.vsyncHonored = false;
        withBackend(notHonored, () -> {
            FramePacer.endFrame(60, null);
            assertEquals(0, notHonored.awaitPresentCalls);
            assertEquals(1, notHonored.readGateSampleCalls);
        });

        final FakeBackend off = new FakeBackend();
        off.mode = VSyncMode.OFF;
        withBackend(off, () -> {
            FramePacer.endFrame(60, null);
            assertEquals(0, off.awaitPresentCalls);
            assertEquals(1, off.readGateSampleCalls);
        });
    }

    @Test
    void endFramePumpsExactlyOnceAfterTheGateReadAndThePacingSleep() {
        final FakeBackend backend = new FakeBackend();
        backend.mode = VSyncMode.OFF;
        withBackend(backend, () -> {
            FramePacer.endFrame(60, null);
            assertEquals(1, backend.pumpCalls);
            assertTrue(backend.readBeforePump, "the gate sample must be read before the window is pumped");

            FramePacer.endFrame(60, null);
            assertEquals(2, backend.pumpCalls);
            assertTrue(backend.parkedBeforePump, "the pump must follow the pacing sleep, not precede it");
        });
    }

    @Test
    void gpuWaitFedToTheCoreEqualsBackendGpuWaitPlusTheFenceWait() {
        final FakeBackend backend = new FakeBackend();
        backend.mode = VSyncMode.OFF;
        backend.gpuWaitNanos = 500_000L;
        final long fenceNanos = 5_000_000L;
        withBackend(backend, () -> {
            FramePacer.endFrame(0, () -> backend.now += fenceNanos);
            final long fed = FramePacer.core.lastGpuWaitNanos();
            assertEquals(backend.gpuWaitNanos + fenceNanos + CLOCK_TICK_NANOS, fed);
        });
    }

    @Test
    void endFrameReturnsThePresentInterval() {
        final FakeBackend backend = new FakeBackend();
        backend.mode = VSyncMode.OFF;
        withBackend(backend, () -> {
            FramePacer.endFrame(0, null);
            final long firstPresent = backend.nanoTime();
            FramePacer.core.beforePresent(firstPresent, 0L);
            FramePacer.endFrame(0, null);
            backend.now += 7_000_000L;
            final long secondPresent = backend.nanoTime();
            FramePacer.core.beforePresent(secondPresent, 0L);
            final long returned = FramePacer.endFrame(0, null);
            assertEquals(secondPresent - firstPresent, returned);
        });
    }

    @Test
    void aDisplayGenerationChangeReArmsTheProbe() {
        final FakeBackend backend = new FakeBackend();
        backend.mode = VSyncMode.ON;
        backend.vsyncHonored = true;
        backend.refreshPeriodNanos = 16_666_666L;
        withBackend(backend, () -> {
            for (int i = 0; i < 60; i++) FramePacer.endFrame(0, null);
            assertFalse(FramePacer.core.probing(), "the probe must exhaust its lead without ever locking");

            backend.displayGeneration++;
            FramePacer.endFrame(0, null);
            assertTrue(FramePacer.core.probing(), "a display change must re-arm the probe");
        });
    }
}
