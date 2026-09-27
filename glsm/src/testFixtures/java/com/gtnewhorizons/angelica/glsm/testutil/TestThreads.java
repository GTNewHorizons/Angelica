package com.gtnewhorizons.angelica.glsm.testutil;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

public final class TestThreads {

    private static final long JOIN_TIMEOUT_MS = 10_000;

    private TestThreads() {}

    @FunctionalInterface
    public interface Body {
        void run() throws Throwable;
    }

    public static final class Worker {
        private final Thread thread;
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        private Worker(String name, Body body) {
            thread = new Thread(() -> {
                try {
                    body.run();
                } catch (Throwable t) {
                    error.set(t);
                }
            }, name);
            thread.setDaemon(true);
            thread.start();
        }

        public Throwable error() {
            return error.get();
        }

        public void join() throws InterruptedException {
            thread.join(JOIN_TIMEOUT_MS);
            assertFalse(thread.isAlive(), thread.getName() + " did not finish");
            if (error.get() != null) fail(error.get());
        }
    }

    public static Worker start(String name, Body body) {
        return new Worker(name, body);
    }

    public static void run(String name, Body body) throws InterruptedException {
        start(name, body).join();
    }
}
