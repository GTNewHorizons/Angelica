package com.gtnewhorizons.angelica.rendering.celeritas;

import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkBuilder;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobCollector;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobTyped;

import com.gtnewhorizons.angelica.rendering.celeritas.DeferredSectionMesh.DeferredCapture;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DeferredMeshScheduler {
    /** Must stay below every distance priority: queued finishes hold pooled buffers and slices. */
    private static final long FINISH_PRIORITY = -1L;

    private static final Comparator<DeferredSectionMesh> NEAREST_FIRST = Comparator.comparingLong(m -> m.priority);
    private static final int CAPTURES_PER_WORKER = 2;
    private static final int MIN_FREE_PER_WORKER = 2;

    private final ChunkBuilder builder;
    private final Consumer<Runnable> mainThreadQueue;
    private final Consumer<Runnable> mainThreadUnqueue;
    private final Queue<ChunkJobResult<? extends ChunkTaskOutput>> results;
    private final Consumer<ChunkJobResult<? extends ChunkTaskOutput>> resultSink;
    private final ObjectPool<ChunkBuildBuffers> buffers;
    private final ObjectPool<WorldSlice> slices;
    private final ObjectPool<DeferredCapture> captures;
    private final ConcurrentLinkedQueue<DeferredSectionMesh> important = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<DeferredSectionMesh> normal = new PriorityBlockingQueue<>(11, NEAREST_FIRST);
    private final Set<DeferredSectionMesh> inFlight = ConcurrentHashMap.newKeySet();
    private final Runnable runNext = this::runNext;
    private final AtomicInteger pending = new AtomicInteger();
    private final int minFree;

    private boolean destroyed;

    public DeferredMeshScheduler(RenderPassConfiguration<?> config, ChunkBuilder builder, Supplier<WorldSlice> sliceFactory, Consumer<Runnable> mainThreadQueue, Consumer<Runnable> mainThreadUnqueue, Queue<ChunkJobResult<? extends ChunkTaskOutput>> results) {
        this.builder = builder;
        this.mainThreadQueue = mainThreadQueue;
        this.mainThreadUnqueue = mainThreadUnqueue;
        this.results = results;
        this.resultSink = results::add;
        final int workers = Math.max(1, builder.getTotalThreadCount());
        this.minFree = workers * MIN_FREE_PER_WORKER;
        this.buffers = new ObjectPool<>(() -> new ChunkBuildBuffers(config), Integer.MAX_VALUE);
        this.slices = new ObjectPool<>(sliceFactory, Integer.MAX_VALUE);
        this.captures = new ObjectPool<>(DeferredCapture::new, workers * CAPTURES_PER_WORKER);
    }

    public ChunkBuildBuffers acquireBuffers() {
        return buffers.acquire();
    }

    public void releaseBuffers(ChunkBuildBuffers buffers) {
        buffers.destroy();
        this.buffers.release(buffers);
    }

    public WorldSlice acquireSlice() {
        return slices.acquire();
    }

    public void submit(DeferredSectionMesh m) {
        pending.incrementAndGet();
        if (m.important) {
            important.add(m);
        } else {
            normal.add(m);
            mainThreadQueue.accept(runNext);
        }
    }

    public void runImportant() {
        if (important.isEmpty()) return;
        final ChunkJobCollector collector = new ChunkJobCollector(Integer.MAX_VALUE, resultSink);
        DeferredSectionMesh m;
        while ((m = important.poll()) != null) {
            run(m, collector);
        }
        collector.awaitCompletion(builder);
    }

    public void markDestroyed() {
        destroyed = true;
    }

    /** Call after the builder shut down: its queued jobs were dropped without reaching their consumers. */
    public void releaseOrphans() {
        mainThreadUnqueue.accept(runNext);
        DeferredSectionMesh m;
        while ((m = important.poll()) != null) {
            release(m);
        }
        while ((m = normal.poll()) != null) {
            release(m);
        }
        for (final DeferredSectionMesh orphan : inFlight) {
            if (inFlight.remove(orphan)) release(orphan);
        }
    }

    public int pendingCount() {
        return pending.get();
    }

    public int finishingCount() {
        return inFlight.size();
    }

    public int freeSliceCount() {
        return slices.freeCount();
    }

    public void trimPools() {
        final long now = System.nanoTime();
        slices.trim(now, minFree);
        buffers.trim(now, minFree);
    }

    void finished(DeferredSectionMesh m, @Nullable ChunkJobResult<ChunkBuildOutput> result, @Nullable ChunkJobCollector collector) {
        final long nanos = m.workerNanos + m.mainNanos;
        ChunkJobResult<ChunkBuildOutput> out = result instanceof ChunkJobResult.Success<ChunkBuildOutput> success ? new ChunkJobResult.Success<>(success.output(), nanos + success.executionTimeNanos()) : result;
        if (inFlight.remove(m)) {
            // Runs as a celeritas job consumer on a worker, where an escaping throwable kills the worker thread.
            try {
                release(m);
            } catch (Throwable t) {
                out = new ChunkJobResult.Failure<>(t);
            }
        }
        if (collector != null) {
            collector.onJobFinished(out);
        } else if (out != null) {
            results.add(out);
        }
    }

    private void runNext() {
        final DeferredSectionMesh m = normal.poll();
        if (m != null) {
            run(m, null);
        }
    }

    private void run(DeferredSectionMesh m, @Nullable ChunkJobCollector collector) {
        boolean scheduled = false;
        try {
            if (destroyed || m.isObsolete()) return;
            m.capture = captures.acquire();
            final ChunkJobResult<ChunkBuildOutput> failure = m.capture();
            if (failure != null) {
                results.add(failure);
                return;
            }
            final DeferredFinishTask task = new DeferredFinishTask(m, this, collector);
            inFlight.add(m);
            final ChunkJobTyped<DeferredFinishTask, ChunkBuildOutput> job = builder.scheduleTask(task, m.important, FINISH_PRIORITY, task);
            scheduled = true;
            if (collector != null) collector.addSubmittedJob(job);
        } finally {
            if (!scheduled) {
                inFlight.remove(m);
                release(m);
            }
        }
    }

    private void release(DeferredSectionMesh m) {
        releaseBuffers(m.buffers);
        m.slice.releaseContext();
        slices.release(m.slice);
        if (m.capture != null) {
            m.capture.clear();
            captures.release(m.capture);
            m.capture = null;
        }
        pending.decrementAndGet();
    }
}
