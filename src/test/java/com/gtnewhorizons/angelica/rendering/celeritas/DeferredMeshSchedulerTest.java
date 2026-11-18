package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredMeshSchedulerTest {

    private static final int NO_WORKERS = -1;

    private record Row(String label, boolean important, boolean obsoleteAtCapture, boolean obsoleteAtFinish, boolean captureFails, boolean finishThrows, boolean destroyed, int expectedResults, Class<?> expectedClass) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class Probe {
        final AtomicInteger obsoleteChecks = new AtomicInteger();
        final AtomicReference<Thread> finishThread = new AtomicReference<>();
        final List<Long> captureOrder = new ArrayList<>();
        final List<Integer> blocksOnCapture = new ArrayList<>();
    }

    private static DeferredSectionMesh mesh(Probe probe, ChunkBuildBuffers buffers, WorldSlice slice, boolean important, long priority, boolean obsoleteAtCapture, boolean obsoleteAtFinish, boolean captureFails, boolean finishThrows) {
        return new DeferredSectionMesh(null, null, buffers, slice, null, null, null, 0L, important, priority, 0L) {
            @Override
            public boolean isObsolete() {
                return probe.obsoleteChecks.getAndIncrement() == 0 ? obsoleteAtCapture : obsoleteAtFinish;
            }

            @Override
            public ChunkJobResult<ChunkBuildOutput> capture() {
                probe.captureOrder.add(priority);
                probe.blocksOnCapture.add(capture.blockCount());
                capture.add(0, new int[8 * 4], 4, new int[4], new int[4], 1);
                return captureFails ? new ChunkJobResult.Failure<>(new RuntimeException("capture")) : null;
            }

            @Override
            public ChunkBuildOutput finish(ChunkBuildContext context) {
                probe.finishThread.set(Thread.currentThread());
                if (finishThrows) throw new IllegalStateException("finish");
                return Mockito.mock(ChunkBuildOutput.class);
            }
        };
    }

    private static final class Harness {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        final Probe probe = new Probe();
        final AtomicInteger slicesCreated = new AtomicInteger();
        final ChunkBuilder builder;
        final DeferredMeshScheduler scheduler;

        Harness(int threads) {
            builder = new ChunkBuilder(ChunkBuilder.ManagedBlocker.NONE, () -> new ChunkBuildContext(null), threads);
            scheduler = new DeferredMeshScheduler(null, builder, () -> {
                slicesCreated.incrementAndGet();
                return Mockito.mock(WorldSlice.class);
            }, queue::add, r -> queue.removeIf(t -> t == r), results);
        }

        DeferredSectionMesh submit(boolean important, long priority) {
            final DeferredSectionMesh m = mesh(probe, scheduler.acquireBuffers(), scheduler.acquireSlice(), important, priority, false, false, false, false);
            scheduler.submit(m);
            return m;
        }
    }

    private static Stream<Arguments> rows() {
        return Stream.of(
            Arguments.of(new Row("normal", false, false, false, false, false, false, 1, ChunkJobResult.Success.class)),
            Arguments.of(new Row("important", true, false, false, false, false, false, 1, ChunkJobResult.Success.class)),
            Arguments.of(new Row("capture fails", false, false, false, true, false, false, 1, ChunkJobResult.Failure.class)),
            Arguments.of(new Row("normal finish throws", false, false, false, false, true, false, 1, ChunkJobResult.Failure.class)),
            Arguments.of(new Row("important finish throws", true, false, false, false, true, false, 1, ChunkJobResult.Failure.class)),
            Arguments.of(new Row("obsolete at capture", false, true, false, false, false, false, 0, null)),
            Arguments.of(new Row("normal obsolete at finish", false, false, true, false, false, false, 0, null)),
            Arguments.of(new Row("important obsolete at finish", true, false, true, false, false, false, 0, null)),
            Arguments.of(new Row("normal destroyed", false, false, false, false, false, true, 0, null)),
            Arguments.of(new Row("important destroyed", true, false, false, false, false, true, 0, null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void submittedMeshFinishesAndReleasesEverything(Row row) {
        Harness h = new Harness(NO_WORKERS);

        ChunkBuildBuffers buffers = h.scheduler.acquireBuffers();
        WorldSlice slice = h.scheduler.acquireSlice();
        DeferredSectionMesh m = mesh(h.probe, buffers, slice, row.important, 0L, row.obsoleteAtCapture, row.obsoleteAtFinish, row.captureFails, row.finishThrows);
        h.scheduler.submit(m);
        if (row.destroyed) h.scheduler.markDestroyed();

        assertEquals(row.important ? 0 : 1, h.queue.size());

        if (row.important) {
            h.scheduler.runImportant();
        } else {
            h.queue.poll().run();
            assertEquals(row.captureFails ? 1 : 0, h.results.size());
            h.builder.tick();
        }

        assertEquals(row.expectedResults, h.results.size());
        if (row.expectedClass != null) assertTrue(row.expectedClass.isInstance(h.results.peek()));
        assertEquals(0, h.scheduler.pendingCount());
        assertEquals(0, h.scheduler.finishingCount());
        assertNull(m.capture);
        assertSame(buffers, h.scheduler.acquireBuffers());
        assertSame(slice, h.scheduler.acquireSlice());
        Mockito.verify(slice, Mockito.times(1)).releaseContext();
        boolean finishRan = !row.captureFails && !row.obsoleteAtCapture && !row.destroyed && !row.obsoleteAtFinish;
        assertEquals(finishRan ? Thread.currentThread() : null, h.probe.finishThread.get());
    }

    @Test
    void finishRunsOnWorkerThread() throws InterruptedException {
        Harness h = new Harness(1);
        try {
            h.submit(false, 0L);
            h.queue.poll().run();

            long deadline = System.nanoTime() + 10_000_000_000L;
            while (h.results.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);

            assertTrue(h.results.peek() instanceof ChunkJobResult.Success);
            assertTrue(h.probe.finishThread.get() instanceof ChunkBuilder.WorkerThread);
            assertEquals(0, h.scheduler.pendingCount());
        } finally {
            h.builder.shutdown();
        }
    }

    @Test
    void normalQueueDrainsNearestFirst() {
        Harness h = new Harness(NO_WORKERS);

        for (long p : new long[] {30L, 10L, 20L}) h.submit(false, p);
        while (!h.queue.isEmpty()) {
            h.queue.poll().run();
            h.builder.tick();
        }

        assertEquals(List.of(10L, 20L, 30L), h.probe.captureOrder);
        assertEquals(List.of(0, 0, 0), h.probe.blocksOnCapture);
    }

    @Test
    void slicesSurviveBurstsBeyondWorkerBound() {
        Harness h = new Harness(NO_WORKERS);

        for (int i = 0; i < 5; i++) h.submit(false, i);
        while (!h.queue.isEmpty()) h.queue.poll().run();
        h.builder.tick();
        for (int i = 0; i < 5; i++) h.scheduler.acquireSlice();

        assertEquals(5, h.slicesCreated.get());
    }

    @Test
    void importantReleaseFailureSurfacesAndReturns() {
        Harness h = new Harness(NO_WORKERS);

        DeferredSectionMesh m = h.submit(true, 0L);
        Mockito.doThrow(new IllegalStateException("release")).when(m.slice).releaseContext();
        h.scheduler.runImportant();

        assertEquals(1, h.results.size());
        assertTrue(h.results.peek() instanceof ChunkJobResult.Failure);
        assertEquals(0, h.scheduler.finishingCount());
    }

    @Test
    void releaseOrphansReleasesEachMeshOnce() {
        Harness h = new Harness(NO_WORKERS);

        DeferredSectionMesh queued = h.submit(false, 2L);
        DeferredSectionMesh inFlight = h.submit(false, 1L);
        h.queue.poll().run();
        assertEquals(1, h.scheduler.finishingCount());

        h.builder.shutdown();
        h.scheduler.releaseOrphans();
        h.scheduler.releaseOrphans();

        assertEquals(0, h.scheduler.pendingCount());
        assertEquals(0, h.scheduler.finishingCount());
        assertEquals(0, h.results.size());
        assertTrue(h.queue.isEmpty(), "queued runNext keeps the destroyed scheduler and its pools reachable");
        Mockito.verify(queued.slice, Mockito.times(1)).releaseContext();
        Mockito.verify(inFlight.slice, Mockito.times(1)).releaseContext();
        assertNull(inFlight.capture);
    }
}
