package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.glsm.backend.MainThreadPump;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLSurface.SDL_FLIP_NONE;

@Timeout(10)
class PresenterTest {

    private static MainThreadPump noPump() {
        return new MainThreadPump(Runnable::run, () -> true, () -> {}, () -> {});
    }

    private static final class DeferredExecutor implements Executor {
        final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            tasks.removeFirst().run();
        }
    }

    private static Presenter presenter(DeferredExecutor executor) {
        return new Presenter(new FrameManager(new Device()), executor, noPump());
    }

    private static void awaitWaiting(Thread t) throws InterruptedException {
        while (t.getState() != Thread.State.WAITING && t.getState() != Thread.State.TIMED_WAITING) {
            assertTrue(t.isAlive(), "thread died instead of blocking");
            Thread.onSpinWait();
        }
    }

    @Test
    void aSecondRequestBlocksUntilTheFirstPresentRuns() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final Presenter p = presenter(executor);

        p.requestPresent(1L, 10, 10, SDL_FLIP_NONE);
        assertEquals(1, executor.tasks.size());

        final Thread second = new Thread(() -> p.requestPresent(2L, 10, 10, SDL_FLIP_NONE));
        second.start();
        awaitWaiting(second);
        assertEquals(1, executor.tasks.size(), "second present must not enqueue while the first is in flight");

        executor.runNext();
        second.join();
        assertEquals(1, executor.tasks.size(), "second present enqueues only after the first released the slot");
    }

    @Test
    void drainBlocksUntilThePendingPresentCompletes() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final Presenter p = presenter(executor);

        p.requestPresent(1L, 10, 10, SDL_FLIP_NONE);

        final Thread drainer = new Thread(p::drain);
        drainer.start();
        awaitWaiting(drainer);

        executor.runNext();
        drainer.join();
    }

    @Test
    void requestReusesOneRunnableAcrossCalls() {
        final DeferredExecutor executor = new DeferredExecutor();
        final Presenter p = presenter(executor);

        p.requestPresent(1L, 10, 10, SDL_FLIP_NONE);
        final Runnable first = executor.tasks.peekFirst();
        executor.runNext();

        p.requestPresent(2L, 10, 10, SDL_FLIP_NONE);
        final Runnable second = executor.tasks.peekFirst();

        assertSame(first, second, "Presenter must submit the same preallocated Runnable on every request");
    }

    @Test
    void anInlineExecutorReleasesTheSlotImmediately() {
        final Presenter p = new Presenter(new FrameManager(new Device()), Runnable::run, noPump());
        p.requestPresent(1L, 10, 10, SDL_FLIP_NONE);
        p.requestPresent(2L, 10, 10, SDL_FLIP_NONE);
        p.drain();
    }

    @Test
    void presentingDoesNotEnterTheFrameRegistry() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final FrameManager fm = new FrameManager(new Device());
        final Presenter p = new Presenter(fm, executor, noPump());
        fm.frame();
        final int before = fm.registeredFrameCount();

        p.requestPresent(1L, 10, 10, SDL_FLIP_NONE);

        final Thread windowThread = new Thread(executor::runNext, "window-thread");
        windowThread.start();
        windowThread.join();

        assertEquals(before, fm.registeredFrameCount(), "the window thread must stay out of the frame registry");
    }

    @Test
    void theSplashPresentGoesThroughTheWindowThreadToo() {
        final DeferredExecutor executor = new DeferredExecutor();
        final FrameManager fm = new FrameManager(new Device());
        fm.setPresenter(new Presenter(fm, executor, noPump()));
        fm.frame();
        final int before = fm.registeredFrameCount();

        fm.presentSplash(1L, 10, 10);

        assertEquals(1, executor.tasks.size(), "the splash blit must be enqueued on the window thread");
        assertEquals(before, fm.registeredFrameCount(), "the splash present must not touch the calling thread's frame");
    }

    @Test
    void tryRequestEnqueuesWhenIdle() {
        final DeferredExecutor executor = new DeferredExecutor();
        final Presenter p = presenter(executor);

        assertTrue(p.tryRequestPresent(1L, 10, 10, SDL_FLIP_NONE));
        assertEquals(1, executor.tasks.size());
    }

    @Test
    void tryRequestDropsWithoutBlockingWhileAPresentIsPending() {
        final DeferredExecutor executor = new DeferredExecutor();
        final Presenter p = presenter(executor);

        p.requestPresent(1L, 10, 10, SDL_FLIP_NONE);
        assertFalse(p.tryRequestPresent(2L, 10, 10, SDL_FLIP_NONE));
        assertEquals(1, executor.tasks.size(), "a dropped present must not enqueue");

        executor.runNext();
        assertTrue(p.tryRequestPresent(3L, 10, 10, SDL_FLIP_NONE));
        assertEquals(1, executor.tasks.size());
    }

    @Test
    void drainBlocksUntilAnAcceptedTryRequestRuns() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final Presenter p = presenter(executor);

        assertTrue(p.tryRequestPresent(1L, 10, 10, SDL_FLIP_NONE));

        final Thread drainer = new Thread(p::drain);
        drainer.start();
        awaitWaiting(drainer);

        executor.runNext();
        drainer.join();
    }

    @Test
    void aBlockingRequestWaitsBehindAnAcceptedTryRequest() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final Presenter p = presenter(executor);

        assertTrue(p.tryRequestPresent(1L, 10, 10, SDL_FLIP_NONE));

        final Thread second = new Thread(() -> p.requestPresent(2L, 10, 10, SDL_FLIP_NONE));
        second.start();
        awaitWaiting(second);
        assertEquals(1, executor.tasks.size());

        executor.runNext();
        second.join();
        assertEquals(1, executor.tasks.size());
    }

    @Test
    void thePresentTaskPumpsFirstAndEndsThePresentWindow() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final AtomicInteger pumps = new AtomicInteger();
        final MainThreadPump pump = new MainThreadPump(Runnable::run, () -> false, pumps::incrementAndGet, () -> {});
        final Presenter p = new Presenter(new FrameManager(new Device()), executor, pump);

        p.requestPresent(1L, 10, 10, SDL_FLIP_NONE);
        final Thread client = new Thread(pump::pumpMessages, "client");
        client.start();
        awaitWaiting(client);
        assertEquals(0, pumps.get(), "a client pump must wait for the present task's pump, not run its own");

        executor.runNext();
        client.join();
        final int afterPresent = pumps.get();
        assertTrue(afterPresent >= 1, "the present task must pump");

        pump.pumpMessages();
        assertEquals(afterPresent + 1, pumps.get(), "after the present ends, client pumps run normally again");
    }

    @Test
    void aRejectedSubmitEndsThePresentWindowAndReleasesTheSlot() {
        final AtomicInteger pumps = new AtomicInteger();
        final MainThreadPump pump = new MainThreadPump(Runnable::run, () -> false, pumps::incrementAndGet, () -> {});
        final Presenter p = new Presenter(new FrameManager(new Device()), r -> { throw new RejectedExecutionException(); }, pump);

        assertThrows(RejectedExecutionException.class, () -> p.requestPresent(1L, 10, 10, SDL_FLIP_NONE));

        pump.pumpMessages();
        assertEquals(1, pumps.get());
        assertThrows(RejectedExecutionException.class, () -> p.tryRequestPresent(2L, 10, 10, SDL_FLIP_NONE));
    }
}
