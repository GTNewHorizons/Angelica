package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredMeshSchedulerTest {

    private static DeferredSectionMesh mesh(ChunkBuildBuffers buffers, WorldSlice slice, boolean important, boolean obsolete, ChunkJobResult<ChunkBuildOutput> result) {
        return new DeferredSectionMesh(null, null, buffers, slice, null, null, null, 0L, important, 0L) {
            @Override
            public boolean isObsolete() {
                return obsolete;
            }

            @Override
            public ChunkJobResult<ChunkBuildOutput> complete(AngelicaChunkBuildContext ctx) {
                return result;
            }
        };
    }

    private static ChunkJobResult<ChunkBuildOutput> success() {
        return new ChunkJobResult.Success<>(null, 0L);
    }

    private static ChunkJobResult<ChunkBuildOutput> failure() {
        return new ChunkJobResult.Failure<>(new RuntimeException("x"));
    }

    @Test
    void importantItemRunsOnRunImportant() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);

        ChunkBuildBuffers buffers = scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, scheduler.acquireSlice(), true, false, success());
        scheduler.submit(m);

        assertEquals(0, queue.size());
        assertEquals(1, scheduler.pendingCount());

        scheduler.runImportant();

        assertEquals(1, results.size());
        assertTrue(results.peek() instanceof ChunkJobResult.Success);
        assertEquals(0, scheduler.pendingCount());
        assertSame(buffers, scheduler.acquireBuffers());
    }

    @Test
    void normalItemRunsThroughMainThreadQueue() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);

        ChunkBuildBuffers buffers = scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, scheduler.acquireSlice(), false, false, success());
        scheduler.submit(m);

        assertEquals(1, queue.size());

        scheduler.runImportant();
        assertEquals(0, results.size());

        queue.poll().run();

        assertEquals(1, results.size());
        assertTrue(results.peek() instanceof ChunkJobResult.Success);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void importantItemAfterDestroyReleasesBuffers() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);

        ChunkBuildBuffers buffers = scheduler.acquireBuffers();
        WorldSlice slice = scheduler.acquireSlice();
        DeferredSectionMesh m = mesh(buffers, slice, true, false, success());
        scheduler.submit(m);
        scheduler.markDestroyed();
        scheduler.runImportant();

        assertEquals(0, results.size());
        assertEquals(0, scheduler.pendingCount());
        assertSame(buffers, scheduler.acquireBuffers());
        assertSame(slice, scheduler.acquireSlice());
    }

    @Test
    void markDestroyedDiscardsQueuedItemAndReturnsBuffersToPool() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);

        ChunkBuildBuffers buffers = scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, scheduler.acquireSlice(), false, false, success());
        scheduler.submit(m);
        scheduler.markDestroyed();
        queue.poll().run();

        assertEquals(0, results.size());
        assertEquals(0, scheduler.pendingCount());
        assertSame(buffers, scheduler.acquireBuffers());
    }

    @Test
    void obsoleteItemIsDiscarded() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);

        ChunkBuildBuffers buffers = scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, scheduler.acquireSlice(), false, true, success());
        scheduler.submit(m);
        queue.poll().run();

        assertEquals(0, results.size());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void failureResultIsRecordedAndCountersStillDecrement() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);

        ChunkBuildBuffers buffers = scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, scheduler.acquireSlice(), false, false, failure());
        scheduler.submit(m);
        queue.poll().run();

        assertEquals(1, results.size());
        assertTrue(results.peek() instanceof ChunkJobResult.Failure);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void acquireBuffersAfterCompletedRunReturnsTheSameInstance() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);

        ChunkBuildBuffers buffers = scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, scheduler.acquireSlice(), false, false, success());
        scheduler.submit(m);
        queue.poll().run();

        assertSame(buffers, scheduler.acquireBuffers());
    }

    @Test
    void runReturnsSliceToPool() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);

        ChunkBuildBuffers buffers = scheduler.acquireBuffers();
        WorldSlice slice = scheduler.acquireSlice();
        DeferredSectionMesh m = mesh(buffers, slice, false, false, success());
        scheduler.submit(m);
        queue.poll().run();

        assertSame(slice, scheduler.acquireSlice());
    }
}
