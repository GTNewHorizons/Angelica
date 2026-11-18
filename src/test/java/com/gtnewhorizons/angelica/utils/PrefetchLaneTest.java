package com.gtnewhorizons.angelica.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class PrefetchLaneTest {

    private static final class ManualSubmitter<V> implements Function<Supplier<V>, CompletableFuture<V>> {

        private final ArrayList<Supplier<V>> queuedSuppliers = new ArrayList<>();
        private final ArrayList<CompletableFuture<V>> queuedFutures = new ArrayList<>();
        private int submitCount;

        @Override
        public CompletableFuture<V> apply(Supplier<V> supplier) {
            submitCount++;
            final CompletableFuture<V> future = new CompletableFuture<>();
            queuedSuppliers.add(supplier);
            queuedFutures.add(future);
            return future;
        }

        int submitCount() {
            return submitCount;
        }

        void runNext() {
            run(0);
        }

        void run(int index) {
            final Supplier<V> supplier = queuedSuppliers.remove(index);
            final CompletableFuture<V> future = queuedFutures.remove(index);
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
    }

    @Test
    void submissionsNeverRunMoreThanWindowAheadOfCursor() {
        final ManualSubmitter<String> submitter = new ManualSubmitter<>();
        final List<String> disposed = new ArrayList<>();
        final PrefetchLane<Object, String> lane = new PrefetchLane<>(submitter, 2, disposed::add);
        final Object[] keys = new Object[5];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new Object();
            final String value = "v" + i;
            lane.add(keys[i], () -> value);
        }

        lane.start();
        assertEquals(2, submitter.submitCount());
        assertEquals(2, lane.submitted());

        for (int i = 0; i < keys.length; i++) {
            submitter.runNext();
            assertEquals("v" + i, lane.take(keys[i]));
            assertEquals(Math.min(2 + i + 1, keys.length), lane.submitted());
        }
    }

    @Test
    void skippedSlotsAreDisposedExactlyOnce() {
        final ManualSubmitter<String> submitter = new ManualSubmitter<>();
        final List<String> disposed = new ArrayList<>();
        final PrefetchLane<Object, String> lane = new PrefetchLane<>(submitter, 4, disposed::add);
        final Object keyA = new Object();
        final Object keyB = new Object();
        final Object keyC = new Object();
        final Object keyD = new Object();
        lane.add(keyA, () -> "vA");
        lane.add(keyB, () -> "vB");
        lane.add(keyC, () -> "vC");
        lane.add(keyD, () -> "vD");

        lane.start();
        submitter.runNext();
        submitter.runNext();
        submitter.runNext();
        submitter.runNext();

        assertEquals("vD", lane.take(keyD));
        Collections.sort(disposed);
        assertEquals(List.of("vA", "vB", "vC"), disposed);
    }

    @Test
    void slotDiscardedBeforeItsTaskStartsNeverRunsTheTask() {
        final ManualSubmitter<String> submitter = new ManualSubmitter<>();
        final List<String> disposed = new ArrayList<>();
        final PrefetchLane<Object, String> lane = new PrefetchLane<>(submitter, 2, disposed::add);
        final Object keyA = new Object();
        final Object keyB = new Object();
        final AtomicBoolean keyARan = new AtomicBoolean();
        lane.add(keyA, () -> {
            keyARan.set(true);
            return "vA";
        });
        lane.add(keyB, () -> "vB");

        lane.start();
        submitter.run(1);

        assertEquals("vB", lane.take(keyB));
        assertFalse(keyARan.get());
        assertTrue(disposed.isEmpty());

        submitter.runNext();
        assertFalse(keyARan.get());
        assertTrue(disposed.isEmpty());
    }

    @Test
    void discardWhileRunningDisposesResultOnCompletionExactlyOnce() throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final CountDownLatch taskStarted = new CountDownLatch(1);
            final CountDownLatch releaseTask = new CountDownLatch(1);
            final List<String> disposed = Collections.synchronizedList(new ArrayList<>());
            final CountDownLatch disposedOnce = new CountDownLatch(1);

            final Function<Supplier<String>, CompletableFuture<String>> submitter = supplier -> CompletableFuture
                .supplyAsync(supplier, pool);
            final PrefetchLane<Object, String> lane = new PrefetchLane<>(submitter, 2, value -> {
                disposed.add(value);
                disposedOnce.countDown();
            });
            final Object keyA = new Object();
            final Object keyB = new Object();
            lane.add(keyA, () -> {
                taskStarted.countDown();
                assertTrue(releaseTask.await(5, TimeUnit.SECONDS));
                return "vA";
            });
            lane.add(keyB, () -> "vB");

            lane.start();
            assertTrue(taskStarted.await(5, TimeUnit.SECONDS));

            assertEquals("vB", lane.take(keyB));

            releaseTask.countDown();
            assertTrue(disposedOnce.await(5, TimeUnit.SECONDS));
            Thread.sleep(50);
            assertEquals(List.of("vA"), disposed);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void placeholdersNeverSubmitAndTakeReturnsNull() {
        final ManualSubmitter<String> submitter = new ManualSubmitter<>();
        final PrefetchLane<Object, String> lane = new PrefetchLane<>(submitter, 3, v -> {});
        final Object keyA = new Object();
        final Object keyB = new Object();
        final Object keyC = new Object();
        lane.add(keyA, () -> "vA");
        lane.add(keyB, null);
        lane.add(keyC, () -> "vC");

        lane.start();
        assertEquals(2, submitter.submitCount());

        submitter.runNext();
        submitter.runNext();

        assertEquals("vA", lane.take(keyA));
        assertNull(lane.take(keyB));
        assertEquals("vC", lane.take(keyC));
    }

    @Test
    void failingTaskReturnsNullFromTakeAndIsCounted() {
        final ManualSubmitter<String> submitter = new ManualSubmitter<>();
        final PrefetchLane<Object, String> lane = new PrefetchLane<>(submitter, 1, v -> {});
        final Object keyA = new Object();
        final RuntimeException boom = new RuntimeException("boom");
        lane.add(keyA, () -> {
            throw boom;
        });

        lane.start();
        submitter.runNext();

        assertNull(lane.take(keyA));
        assertEquals(1, lane.failures());
        assertSame(boom, lane.firstFailure());
    }

    @Test
    void takeForUnknownKeyDesyncsAndDisposesEverythingOutstandingExactlyOnce() {
        final ManualSubmitter<String> submitter = new ManualSubmitter<>();
        final List<String> disposed = new ArrayList<>();
        final PrefetchLane<Object, String> lane = new PrefetchLane<>(submitter, 3, disposed::add);
        final Object keyA = new Object();
        final Object keyB = new Object();
        final Object keyC = new Object();
        lane.add(keyA, () -> "vA");
        lane.add(keyB, () -> "vB");
        lane.add(keyC, () -> "vC");

        lane.start();
        submitter.runNext();
        submitter.runNext();
        submitter.runNext();

        assertNull(lane.take(new Object()));
        assertTrue(lane.isDesynced());

        Collections.sort(disposed);
        assertEquals(List.of("vA", "vB", "vC"), disposed);
    }

    @Test
    void closeNeverBlocksEvenWithARunningTaskAndDisposesItOnceItFinishes() throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final CountDownLatch taskStarted = new CountDownLatch(1);
            final CountDownLatch releaseTask = new CountDownLatch(1);
            final List<String> disposed = Collections.synchronizedList(new ArrayList<>());
            final AtomicInteger disposeCount = new AtomicInteger();
            final CountDownLatch disposedOnce = new CountDownLatch(1);

            final Function<Supplier<String>, CompletableFuture<String>> submitter = supplier -> CompletableFuture
                .supplyAsync(supplier, pool);
            final PrefetchLane<Object, String> lane = new PrefetchLane<>(submitter, 2, value -> {
                disposed.add(value);
                disposeCount.incrementAndGet();
                disposedOnce.countDown();
            });
            final Object keyA = new Object();
            lane.add(keyA, () -> {
                taskStarted.countDown();
                assertTrue(releaseTask.await(5, TimeUnit.SECONDS));
                return "vA";
            });

            lane.start();
            assertTrue(taskStarted.await(5, TimeUnit.SECONDS));

            assertTimeoutPreemptively(Duration.ofSeconds(2), lane::close);

            releaseTask.countDown();
            assertTrue(disposedOnce.await(5, TimeUnit.SECONDS));
            Thread.sleep(50);
            assertEquals(1, disposeCount.get());
            assertEquals(List.of("vA"), disposed);
        } finally {
            pool.shutdownNow();
        }
    }
}
