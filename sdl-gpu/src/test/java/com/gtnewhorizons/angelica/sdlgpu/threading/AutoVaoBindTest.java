package com.gtnewhorizons.angelica.sdlgpu.threading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AutoVaoBindTest {

    private static final class Gate {
        final ThreadLocal<Boolean> autoBound = ThreadLocal.withInitial(() -> Boolean.FALSE);
        final Thread mainThread;
        Gate(Thread main) { this.mainThread = main; }

        boolean wouldAutoBind() {
            if (Thread.currentThread() == mainThread) return false;
            if (autoBound.get()) return false;
            autoBound.set(Boolean.TRUE);
            return true;
        }
    }

    @Test
    void mainThreadDoesNotAutoBind() {
        final Gate g = new Gate(Thread.currentThread());
        assertFalse(g.wouldAutoBind());
        assertFalse(g.wouldAutoBind(), "still false on subsequent calls");
    }

    @Test
    void nonMainThreadAutoBindsOnce() throws Exception {
        final Gate g = new Gate(Thread.currentThread());
        final AtomicBoolean firstCall = new AtomicBoolean();
        final AtomicBoolean secondCall = new AtomicBoolean();
        final Thread t = new Thread(() -> {
            firstCall.set(g.wouldAutoBind());
            secondCall.set(g.wouldAutoBind());
        }, "non-main");
        t.start();
        t.join();
        assertTrue(firstCall.get(), "first call from non-main thread auto-binds");
        assertFalse(secondCall.get(), "second call does not");
    }

    @Test
    void distinctNonMainThreadsEachAutoBind() throws Exception {
        final Gate g = new Gate(Thread.currentThread());
        final AtomicBoolean a = new AtomicBoolean();
        final AtomicBoolean b = new AtomicBoolean();
        final Thread tA = new Thread(() -> a.set(g.wouldAutoBind()), "non-main-a");
        final Thread tB = new Thread(() -> b.set(g.wouldAutoBind()), "non-main-b");
        tA.start(); tB.start(); tA.join(); tB.join();
        assertTrue(a.get());
        assertTrue(b.get());
    }
}
