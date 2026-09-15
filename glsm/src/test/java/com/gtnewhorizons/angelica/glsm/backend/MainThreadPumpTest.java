package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class MainThreadPumpTest {

    private static final class DeferredExecutor implements Executor {
        final Deque<Runnable> tasks = new ConcurrentLinkedDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            tasks.removeFirst().run();
        }

        void awaitTask() {
            while (tasks.isEmpty()) {
                Thread.onSpinWait();
            }
        }
    }

    private static final class InlineExecutor implements Executor {
        final List<Runnable> submitted = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void execute(Runnable command) {
            submitted.add(command);
            command.run();
        }
    }

    private static void awaitWaiting(Thread t) {
        while (t.getState() != Thread.State.WAITING && t.getState() != Thread.State.TIMED_WAITING) {
            assertTrue(t.isAlive(), "thread died instead of blocking");
            Thread.onSpinWait();
        }
    }

    private static MainThreadPump pump(Executor executor, Runnable pumpBody, Runnable pollBody) {
        return new MainThreadPump(executor, () -> false, pumpBody, pollBody);
    }

    @Test
    void repeatedPumpsSubmitTheSameRunnable() {
        final InlineExecutor executor = new InlineExecutor();
        final AtomicInteger pumps = new AtomicInteger();
        final AtomicInteger polls = new AtomicInteger();
        final MainThreadPump p = pump(executor, pumps::incrementAndGet, polls::incrementAndGet);

        for (int i = 0; i < 16; i++) {
            p.pumpMessages();
        }

        assertEquals(16, executor.submitted.size());
        assertEquals(16, pumps.get());
        assertEquals(16, polls.get());
        final Runnable first = executor.submitted.get(0);
        for (Runnable r : executor.submitted) {
            assertSame(first, r, "pump must not allocate a new task per call");
        }
    }

    @Test
    void pumpBlocksUntilTheMainThreadRunsTheTask() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final AtomicInteger polls = new AtomicInteger();
        final MainThreadPump p = pump(executor, () -> {}, polls::incrementAndGet);

        final Thread caller = new Thread(p::pumpMessages, "pump-caller");
        caller.start();
        awaitWaiting(caller);
        executor.awaitTask();
        assertEquals(1, executor.tasks.size());
        assertEquals(0, polls.get());

        executor.runNext();
        caller.join();
        assertEquals(1, polls.get());
    }

    @Test
    void aThrowingBodyPropagatesToTheCallerAndDoesNotLeaveItParked() {
        final InlineExecutor executor = new InlineExecutor();
        final AtomicInteger polls = new AtomicInteger();
        final AtomicInteger pumps = new AtomicInteger();
        final MainThreadPump p = pump(executor, () -> {
            if (pumps.incrementAndGet() == 1) throw new IllegalStateException("boom");
        }, polls::incrementAndGet);

        final IllegalStateException thrown = assertThrows(IllegalStateException.class, p::pumpMessages);
        assertEquals("boom", thrown.getMessage());
        assertEquals(0, polls.get(), "poll must not run when the pump body failed");

        p.pumpMessages();
        assertEquals(1, polls.get(), "a failed pump must not poison the next one");
    }

    @Test
    void mainThreadCallersRunInlineWithoutTheExecutor() {
        final InlineExecutor executor = new InlineExecutor();
        final AtomicInteger pumps = new AtomicInteger();
        final AtomicInteger polls = new AtomicInteger();
        final MainThreadPump p = new MainThreadPump(executor, () -> true, pumps::incrementAndGet,
            polls::incrementAndGet);

        p.pumpMessages();

        assertTrue(executor.submitted.isEmpty(), "the main thread must not hop through the executor");
        assertEquals(1, pumps.get());
        assertEquals(1, polls.get());
    }

    @Test
    void anInterruptedCallerCompletesAndKeepsItsInterruptFlag() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final MainThreadPump p = pump(executor, () -> {}, () -> {});
        final AtomicInteger stillInterrupted = new AtomicInteger(-1);

        final Thread caller = new Thread(() -> {
            p.pumpMessages();
            stillInterrupted.set(Thread.currentThread().isInterrupted() ? 1 : 0);
        }, "pump-interrupted-caller");
        caller.start();
        awaitWaiting(caller);
        executor.awaitTask();
        caller.interrupt();
        assertFalse(executor.tasks.isEmpty());

        executor.runNext();
        caller.join();
        assertEquals(1, stillInterrupted.get());
    }
}
