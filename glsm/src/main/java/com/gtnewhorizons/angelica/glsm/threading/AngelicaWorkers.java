package com.gtnewhorizons.angelica.glsm.threading;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class AngelicaWorkers {
    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private static final long IDLE_TIMEOUT_SECONDS = 120;

    private static final Object lock = new Object();
    private static final List<Runnable> idleShutdownHooks = new ArrayList<>();
    private static ExecutorService executor;
    private static ScheduledExecutorService scheduler;
    private static volatile long lastActivityTime;
    private static final AtomicInteger inFlight = new AtomicInteger(0);
    private static boolean idleCheckScheduled;
    private static int resolvedThreads;

    private AngelicaWorkers() {}

    private static final class Worker extends Thread {
        Worker(Runnable r, String name) {
            super(r, name);
            setDaemon(true);
        }
    }

    public static boolean isWorkerThread() {
        return Thread.currentThread() instanceof Worker;
    }

    public static void onIdleShutdown(Runnable hook) {
        Objects.requireNonNull(hook);
        synchronized (lock) {
            idleShutdownHooks.add(hook);
        }
    }

    public static void prestart() {
        executor();
    }

    public static int threads() {
        synchronized (lock) {
            resolveThreads();
            return resolvedThreads;
        }
    }

    private static void resolveThreads() {
        if (resolvedThreads == 0) {
            final int configured = GLSMConfig.workerThreadCount;
            resolvedThreads = configured > 0 ? configured : Math.max(2, Math.min(12, Runtime.getRuntime().availableProcessors() / 2));
        }
    }

    private static void noteActivity() {
        lastActivityTime = System.nanoTime();
    }

    private static ExecutorService executor() {
        synchronized (lock) {
            noteActivity();

            if (executor != null && !executor.isShutdown()) {
                return executor;
            }

            resolveThreads();
            final AtomicInteger threadCounter = new AtomicInteger();
            final ThreadFactory factory = r -> new Worker(r, "Angelica-Worker-" + threadCounter.getAndIncrement());
            final ThreadPoolExecutor tpe = new ThreadPoolExecutor(resolvedThreads, resolvedThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);
            tpe.prestartAllCoreThreads();
            executor = tpe;
            LOGGER.debug("Created Angelica worker pool with {} prestarted threads", resolvedThreads);

            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    final Thread t = new Thread(r, "Angelica-Worker-Scheduler");
                    t.setDaemon(true);
                    return t;
                });
            }
            if (!idleCheckScheduled) {
                idleCheckScheduled = true;
                scheduleIdleCheck(IDLE_TIMEOUT_SECONDS);
            }

            return executor;
        }
    }

    private static void scheduleIdleCheck(long delaySeconds) {
        try {
            scheduler.schedule(AngelicaWorkers::checkIdleShutdown, delaySeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            idleCheckScheduled = false;
            LOGGER.warn("Failed to schedule idle check", e);
        }
    }

    public static <T> CompletableFuture<T> submit(Supplier<T> work) {
        Objects.requireNonNull(work);
        noteActivity();
        inFlight.incrementAndGet();
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return work.get();
                } finally {
                    noteActivity();
                    inFlight.decrementAndGet();
                }
            }, executor());
        } catch (Exception e) {
            inFlight.decrementAndGet();
            throw e;
        }
    }

    public static CompletableFuture<Void> run(Runnable work) {
        Objects.requireNonNull(work);
        noteActivity();
        inFlight.incrementAndGet();
        try {
            return CompletableFuture.runAsync(() -> {
                try {
                    work.run();
                } finally {
                    noteActivity();
                    inFlight.decrementAndGet();
                }
            }, executor());
        } catch (Exception e) {
            inFlight.decrementAndGet();
            throw e;
        }
    }

    /**
     * Runs every task and returns the results in task order.
     */
    public static <T> List<T> invokeAll(List<? extends Supplier<? extends T>> tasks) {
        final int count = tasks.size();
        final Object[] results = new Object[count];
        if (count == 0) return new ArrayList<>();
        final AtomicInteger next = new AtomicInteger();
        final CountDownLatch finished = new CountDownLatch(count);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Runnable drain = () -> {
            int i;
            while ((i = next.getAndIncrement()) < count) {
                try {
                    results[i] = tasks.get(i).get();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    finished.countDown();
                }
            }
        };
        final int helpers = Math.min(threads(), count - 1);
        for (int h = 0; h < helpers; h++) {
            run(drain);
        }
        drain.run();
        try {
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for worker tasks", e);
        }
        final Throwable t = failure.get();
        if (t instanceof RuntimeException re) throw re;
        if (t instanceof Error err) throw err;
        if (t != null) throw new IllegalStateException(t);
        final List<T> out = new ArrayList<>(count);
        for (Object result : results) {
            @SuppressWarnings("unchecked") final T typed = (T) result;
            out.add(typed);
        }
        return out;
    }

    private static void checkIdleShutdown() {
        synchronized (lock) {
            final ExecutorService current = executor;
            if (current == null || current.isShutdown()) {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.shutdown();
                    scheduler = null;
                }
                idleCheckScheduled = false;
                return;
            }

            final long idleSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastActivityTime);

            if (idleSeconds >= IDLE_TIMEOUT_SECONDS && inFlight.get() == 0) {
                LOGGER.debug("Shutting down idle Angelica worker pool after {} seconds", idleSeconds);
                current.shutdown();
                executor = null;
                scheduler.shutdown();
                scheduler = null;
                idleCheckScheduled = false;

                for (int i = 0; i < idleShutdownHooks.size(); i++) {
                    idleShutdownHooks.get(i).run();
                }
            } else {
                scheduleIdleCheck(Math.max(1, IDLE_TIMEOUT_SECONDS - idleSeconds + 1));
            }
        }
    }
}
