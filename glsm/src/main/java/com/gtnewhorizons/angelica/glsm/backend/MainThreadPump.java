package com.gtnewhorizons.angelica.glsm.backend;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

public final class MainThreadPump {

    private static final Logger LOGGER = LogManager.getLogger("MainThreadPump");

    private static final Tracy.ZoneId Z_DISPLAY_MESSAGES = Tracy.zoneId("displayMessages", Tracy.COLOR_CLIENT);

    private static final long PARK_MILLIS = 20L;
    private static final long STALL_WARN_NANOS = 5_000_000_000L;

    public static void pollInput() {
        Keyboard.poll();
        Mouse.poll();
    }

    private final Executor mainExecutor;
    private final BooleanSupplier isMainThread;
    private final Runnable pumpBody;
    private final Runnable pollBody;
    private final Runnable task = this::runPump;
    private final Object lock = new Object();

    private boolean done;
    private Throwable failure;
    private ClassLoader callerLoader;

    public MainThreadPump(Executor mainExecutor, BooleanSupplier isMainThread, Runnable pumpBody, Runnable pollBody) {
        this.mainExecutor = mainExecutor;
        this.isMainThread = isMainThread;
        this.pumpBody = pumpBody;
        this.pollBody = pollBody;
    }

    public void pumpMessages() {
        Tracy.beginZone(Z_DISPLAY_MESSAGES);
        try {
            if (isMainThread.getAsBoolean()) {
                pumpBody.run();
            } else {
                synchronized (lock) {
                    runPumpOnMainThread();
                }
            }
            pollBody.run();
        } finally {
            Tracy.endZone();
        }
    }

    private void runPumpOnMainThread() {
        final Thread self = Thread.currentThread();
        callerLoader = self.getContextClassLoader();
        failure = null;
        done = false;
        mainExecutor.execute(task);
        boolean interrupted = false;
        boolean stallReported = false;
        final long stallAt = System.nanoTime() + STALL_WARN_NANOS;
        while (!done) {
            try {
                lock.wait(PARK_MILLIS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            if (!done && !stallReported && System.nanoTime() - stallAt >= 0L) {
                stallReported = true;
                LOGGER.warn("Display message pump has been waiting on the RFB main thread for over {} ms", STALL_WARN_NANOS / 1_000_000L);
            }
        }
        if (interrupted) self.interrupt();
        final Throwable t = failure;
        if (t != null) {
            throw asUnchecked(t);
        }
    }

    private void runPump() {
        final Thread self = Thread.currentThread();
        final ClassLoader saved = self.getContextClassLoader();
        try {
            self.setContextClassLoader(callerLoader);
            pumpBody.run();
        } catch (Throwable t) {
            failure = t;
        } finally {
            try {
                self.setContextClassLoader(saved);
            } finally {
                synchronized (lock) {
                    done = true;
                    lock.notifyAll();
                }
            }
        }
    }

    private static RuntimeException asUnchecked(Throwable t) {
        if (t instanceof RuntimeException runtime) return runtime;
        if (t instanceof Error error) throw error;
        return new RuntimeException(t);
    }
}
