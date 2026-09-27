package com.gtnewhorizons.angelica.rendering.celeritas;

import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DeferredMeshScheduler {
    private final Supplier<AngelicaChunkBuildContext> mainContextFactory;
    private final Consumer<Runnable> mainThreadQueue;
    private final Queue<ChunkJobResult<? extends ChunkTaskOutput>> results;
    private final ObjectPool<ChunkBuildBuffers> buffers;
    private final ObjectPool<WorldSlice> slices;
    private final ConcurrentLinkedQueue<DeferredSectionMesh> important = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DeferredSectionMesh> normal = new ConcurrentLinkedQueue<>();
    private final Runnable runNext = this::runNext;
    private final AtomicInteger pending = new AtomicInteger();

    private AngelicaChunkBuildContext mainContext;
    private boolean destroyed;

    public DeferredMeshScheduler(RenderPassConfiguration<?> config, Supplier<AngelicaChunkBuildContext> mainContextFactory, Supplier<WorldSlice> sliceFactory, Consumer<Runnable> mainThreadQueue, Queue<ChunkJobResult<? extends ChunkTaskOutput>> results) {
        this.mainContextFactory = mainContextFactory;
        this.mainThreadQueue = mainThreadQueue;
        this.results = results;
        this.buffers = new ObjectPool<>(() -> new ChunkBuildBuffers(config));
        this.slices = new ObjectPool<>(sliceFactory);
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
        DeferredSectionMesh m;
        while ((m = important.poll()) != null) {
            run(m);
        }
    }

    public void markDestroyed() {
        destroyed = true;
        if (mainContext != null) {
            mainContext.cleanup();
            mainContext = null;
        }
    }

    public int pendingCount() {
        return pending.get();
    }

    private AngelicaChunkBuildContext mainContext() {
        if (mainContext == null) {
            mainContext = mainContextFactory.get();
        }
        return mainContext;
    }

    private void runNext() {
        final DeferredSectionMesh m = normal.poll();
        if (m != null) {
            run(m);
        }
    }

    private void run(DeferredSectionMesh m) {
        try {
            if (destroyed || m.isObsolete()) return;
            results.add(m.complete(mainContext()));
        } finally {
            releaseBuffers(m.buffers);
            slices.release(m.slice);
            pending.decrementAndGet();
        }
    }
}
