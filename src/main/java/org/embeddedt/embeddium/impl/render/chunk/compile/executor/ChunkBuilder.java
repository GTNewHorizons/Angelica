package org.embeddedt.embeddium.impl.render.chunk.compile.executor;

import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.GlobalChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChunkBuilder {
    static final Logger LOGGER = LogManager.getLogger("ChunkBuilder");
    /**
     * Megabytes of heap required per chunk builder thread. This is used to cap the number of worker
     * threads when the game is given a small heap.
     */
    private static final int MBS_PER_CHUNK_BUILDER = 64;
    /**
     * The number of tasks to allow in the queue per available worker thread. This value should be kept conservative
     * to avoid the threads becoming backlogged and failing to keep up with changes in chunk visibility (e.g.
     * camera movement). However, it also needs to be large enough that the thread is not spending part of the
     * frame doing nothing. 2 seems to be a decent value, and is what Sodium 0.2 used.
     */
    private static final int TASK_QUEUE_LIMIT_PER_WORKER = 2;

    private final ChunkJobQueue queue = new ChunkJobQueue();

    private final List<WorkerThread> threads = new ArrayList<>();

    private final AtomicInteger busyThreadCount = new AtomicInteger();

    private final ChunkBuildContext localContext;

    private final ManagedBlocker managedBlocker;

    public ChunkBuilder(ManagedBlocker managedBlocker, Supplier<ChunkBuildContext> contextSupplier, int requestedThreads) {
        GlobalChunkBuildContext.setMainThread();

        if (requestedThreads >= 0) {
            int count = getThreadCount(requestedThreads);

            for (int i = 0; i < count; i++) {
                ChunkBuildContext context = contextSupplier.get();
                WorkerRunnable worker = new WorkerRunnable(context);

                WorkerThread thread = new WorkerThread(worker, "Chunk Render Task Executor #" + i, context);
                thread.setPriority(Math.max(0, Thread.NORM_PRIORITY - 2));
                thread.start();

                this.threads.add(thread);
            }
        }

        LOGGER.info("Started {} worker threads", this.threads.size());

        this.localContext = contextSupplier.get();

        this.managedBlocker = managedBlocker;
    }

    /**
     * Returns the remaining number of build tasks which should be scheduled this frame. If an attempt is made to
     * spawn more tasks than the budget allows, it will block until resources become available.
     */
    public int getSchedulingBudget() {
        return Math.max(0, (Math.max(1, this.threads.size()) * TASK_QUEUE_LIMIT_PER_WORKER) - this.queue.size());
    }

    /**
     * <p>Notifies all worker threads to stop and blocks until all workers terminate. After the workers have been shut
     * down, all tasks are cancelled and the pending queues are cleared. If the builder is already stopped, this
     * method does nothing and exits.</p>
     *
     * <p>After shutdown, all previously scheduled jobs will have been cancelled. Jobs that finished while
     * waiting for worker threads to shut down will still have their results processed for later cleanup.</p>
     */
    public void shutdown() {
        if (!this.queue.isRunning()) {
            throw new IllegalStateException("Worker threads are not running");
        }

        // Delete any queued tasks and resources attached to them
        var jobs = this.queue.shutdown();

        for (var job : jobs) {
            job.setCancelled();
        }

        this.shutdownThreads();
    }

    private void shutdownThreads() {
        LOGGER.info("Stopping worker threads");

        // Wait for every remaining thread to terminate
        for (WorkerThread thread : this.threads) {
            this.managedBlocker.managedBlock(() -> !thread.isAlive());
        }

        this.threads.clear();
    }

    public <TASK extends ChunkBuilderTask<OUTPUT>, OUTPUT> ChunkJobTyped<TASK, OUTPUT> scheduleTask(TASK task, boolean important,
                                                                                                    Consumer<ChunkJobResult<OUTPUT>> consumer)
    {
        Preconditions.checkNotNull(task, "Task must be non-null");

        if (!this.queue.isRunning()) {
            throw new IllegalStateException("Executor is stopped");
        }

        var job = new ChunkJobTyped<>(task, consumer);

        this.queue.add(job, important);

        return job;
    }

    /**
     * Returns the "optimal" number of threads to be used for chunk build tasks. This will always return at least one
     * thread.
     */
    private static int getOptimalThreadCount() {
        int desiredThreads = Math.max(getMaxThreadCount() / 3, getMaxThreadCount() - 6);
        if (desiredThreads < 1) {
            return 1;
        } else if (desiredThreads > 10) {
            return 10;
        } else {
            return desiredThreads;
        }
    }

    private static int getThreadCount(int requested) {
        return requested == 0 ? getOptimalThreadCount() : Math.min(requested, getMaxThreadCount());
    }

    public static int getMaxThreadCount() {
        int totalCores = Runtime.getRuntime().availableProcessors();
        long memoryMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        // always allow at least one builder regardless of heap size
        int maxBuilders = Math.max(1, (int)(memoryMb / MBS_PER_CHUNK_BUILDER));
        // choose the total CPU cores or the number of builders the heap permits, whichever is smaller
        return Math.min(totalCores, maxBuilders);
    }

    public void tryStealTask(ChunkJob job) {
        if (!this.queue.stealJob(job)) {
            return;
        }

        executeJobWithLocalContext(job);
    }

    private void executeJobWithLocalContext(ChunkJob job) {
        var localContext = this.localContext;
        GlobalChunkBuildContext.bindMainThread(localContext);

        try {
            job.execute(localContext);
        } finally {
            GlobalChunkBuildContext.bindMainThread(null);
            localContext.cleanup();
        }
    }

    public void tick() {
        // Don't need to run jobs on the main thread if there are worker threads
        if (!this.threads.isEmpty()) {
            return;
        }

        while (!this.queue.isEmpty()) {
            var job = Objects.requireNonNull(this.queue.pollJob());
            executeJobWithLocalContext(job);
        }
    }

    public boolean isBuildQueueEmpty() {
        return this.queue.isEmpty();
    }

    public int getScheduledJobCount() {
        return this.queue.size();
    }

    public int getBusyThreadCount() {
        return this.busyThreadCount.get();
    }

    public int getTotalThreadCount() {
        return this.threads.size();
    }

    public static final class WorkerThread extends Thread implements GlobalChunkBuildContext.Holder {
        private final ChunkBuildContext context;

        public WorkerThread(Runnable runnable, String name, ChunkBuildContext context) {
            super(runnable, name);
            this.context = context;
        }

        @Override
        public ChunkBuildContext embeddium$getGlobalContext() {
            return context;
        }
    }

    private class WorkerRunnable implements Runnable {
        // Making this thread-local provides a small boost to performance by avoiding the overhead in synchronizing
        // caches between different CPU cores
        private final ChunkBuildContext context;

        public WorkerRunnable(ChunkBuildContext context) {
            this.context = context;
        }

        @Override
        public void run() {
            // Run until the chunk builder shuts down
            while (ChunkBuilder.this.queue.isRunning()) {
                ChunkJob job;

                try {
                    job = ChunkBuilder.this.queue.waitForNextJob();
                } catch (InterruptedException ignored) {
                    continue;
                }

                if (job == null) {
                    // might mean we are not running anymore... go around and check isRunning
                    continue;
                }

                ChunkBuilder.this.busyThreadCount.getAndIncrement();

                try {
                    job.execute(this.context);
                } finally {
                    this.context.cleanup();

                    ChunkBuilder.this.busyThreadCount.decrementAndGet();
                }
            }
        }
    }

    public interface ManagedBlocker {
        ManagedBlocker NONE = isDone -> {
            while (!isDone.getAsBoolean()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }
        };

        void managedBlock(BooleanSupplier isDone);
    }
}
