package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.stream.Stream;

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

    private static final class Harness {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final Queue<ChunkJobResult<? extends ChunkTaskOutput>> results = new ConcurrentLinkedDeque<>();
        final DeferredMeshScheduler scheduler = new DeferredMeshScheduler(null, () -> null, () -> Mockito.mock(WorldSlice.class), queue::add, results);
    }

    @Test
    void importantItemRunsOnRunImportant() {
        Harness h = new Harness();

        ChunkBuildBuffers buffers = h.scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, h.scheduler.acquireSlice(), true, false, success());
        h.scheduler.submit(m);

        assertEquals(0, h.queue.size());
        assertEquals(1, h.scheduler.pendingCount());

        h.scheduler.runImportant();

        assertEquals(1, h.results.size());
        assertTrue(h.results.peek() instanceof ChunkJobResult.Success);
        assertEquals(0, h.scheduler.pendingCount());
        assertSame(buffers, h.scheduler.acquireBuffers());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("normalResults")
    void normalItemRunsThroughMainThreadQueue(String label, Supplier<ChunkJobResult<ChunkBuildOutput>> result, Class<?> expected) {
        Harness h = new Harness();

        ChunkBuildBuffers buffers = h.scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, h.scheduler.acquireSlice(), false, false, result.get());
        h.scheduler.submit(m);

        assertEquals(1, h.queue.size());

        h.scheduler.runImportant();
        assertEquals(0, h.results.size());

        h.queue.poll().run();

        assertEquals(1, h.results.size());
        assertTrue(expected.isInstance(h.results.peek()));
        assertEquals(0, h.scheduler.pendingCount());
    }

    private static Stream<Arguments> normalResults() {
        return Stream.of(
            Arguments.of("success", (Supplier<ChunkJobResult<ChunkBuildOutput>>) DeferredMeshSchedulerTest::success, ChunkJobResult.Success.class),
            Arguments.of("failure is recorded and counters still decrement", (Supplier<ChunkJobResult<ChunkBuildOutput>>) DeferredMeshSchedulerTest::failure, ChunkJobResult.Failure.class));
    }

    @Test
    void importantItemAfterDestroyReleasesBuffers() {
        Harness h = new Harness();

        ChunkBuildBuffers buffers = h.scheduler.acquireBuffers();
        WorldSlice slice = h.scheduler.acquireSlice();
        DeferredSectionMesh m = mesh(buffers, slice, true, false, success());
        h.scheduler.submit(m);
        h.scheduler.markDestroyed();
        h.scheduler.runImportant();

        assertEquals(0, h.results.size());
        assertEquals(0, h.scheduler.pendingCount());
        assertSame(buffers, h.scheduler.acquireBuffers());
        assertSame(slice, h.scheduler.acquireSlice());
        Mockito.verify(slice).releaseContext();
    }

    @Test
    void markDestroyedDiscardsQueuedItemAndReturnsBuffersToPool() {
        Harness h = new Harness();

        ChunkBuildBuffers buffers = h.scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, h.scheduler.acquireSlice(), false, false, success());
        h.scheduler.submit(m);
        h.scheduler.markDestroyed();
        h.queue.poll().run();

        assertEquals(0, h.results.size());
        assertEquals(0, h.scheduler.pendingCount());
        assertSame(buffers, h.scheduler.acquireBuffers());
    }

    @Test
    void obsoleteItemIsDiscarded() {
        Harness h = new Harness();

        ChunkBuildBuffers buffers = h.scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, h.scheduler.acquireSlice(), false, true, success());
        h.scheduler.submit(m);
        h.queue.poll().run();

        assertEquals(0, h.results.size());
        assertEquals(0, h.scheduler.pendingCount());
    }

    @Test
    void acquireBuffersAfterCompletedRunReturnsTheSameInstance() {
        Harness h = new Harness();

        ChunkBuildBuffers buffers = h.scheduler.acquireBuffers();
        DeferredSectionMesh m = mesh(buffers, h.scheduler.acquireSlice(), false, false, success());
        h.scheduler.submit(m);
        h.queue.poll().run();

        assertSame(buffers, h.scheduler.acquireBuffers());
    }

    @Test
    void runReturnsSliceToPool() {
        Harness h = new Harness();

        ChunkBuildBuffers buffers = h.scheduler.acquireBuffers();
        WorldSlice slice = h.scheduler.acquireSlice();
        DeferredSectionMesh m = mesh(buffers, slice, false, false, success());
        h.scheduler.submit(m);
        h.queue.poll().run();

        assertSame(slice, h.scheduler.acquireSlice());
        Mockito.verify(slice).releaseContext();
    }
}
