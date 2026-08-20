package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLSurface.SDL_FLIP_NONE;

@Timeout(10)
class PresenterTest {

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
        return new Presenter(new FrameManager(new Device()), executor);
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
    void anInlineExecutorReleasesTheSlotImmediately() {
        final Presenter p = new Presenter(new FrameManager(new Device()), Runnable::run);
        p.requestPresent(1L, 10, 10, SDL_FLIP_NONE);
        p.requestPresent(2L, 10, 10, SDL_FLIP_NONE);
        p.drain();
    }

    @Test
    void presentingDoesNotEnterTheFrameRegistry() throws Exception {
        final DeferredExecutor executor = new DeferredExecutor();
        final FrameManager fm = new FrameManager(new Device());
        final Presenter p = new Presenter(fm, executor);
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
        fm.setPresenter(new Presenter(fm, executor));
        fm.frame();
        final int before = fm.registeredFrameCount();

        fm.presentSplash(1L, 10, 10);

        assertEquals(1, executor.tasks.size(), "the splash blit must be enqueued on the window thread");
        assertEquals(before, fm.registeredFrameCount(), "the splash present must not touch the calling thread's frame");
    }
}
