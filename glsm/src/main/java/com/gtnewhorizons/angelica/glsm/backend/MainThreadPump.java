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

    private boolean presentPumpPending;
    private boolean presentInFlight;
    private Throwable presentFailure;
    private ClassLoader presentLoader;

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
                    await(true);
                    final Throwable t = presentFailure;
                    presentFailure = null;
                    if (t != null) throw asUnchecked(t);
                    if (!presentInFlight) runPumpOnMainThread();
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
        done = false;
        mainExecutor.execute(task);
        await(false);
        final Throwable t = failure;
        if (t != null) {
            throw asUnchecked(t);
        }
    }

    private boolean waiting(boolean presentPump) {
        return presentPump ? presentPumpPending : !done;
    }

    private void await(boolean presentPump) {
        boolean interrupted = false;
        boolean stallReported = false;
        final long stallAt = System.nanoTime() + STALL_WARN_NANOS;
        while (waiting(presentPump)) {
            try {
                lock.wait(PARK_MILLIS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            if (waiting(presentPump) && !stallReported && System.nanoTime() - stallAt >= 0L) {
                stallReported = true;
                LOGGER.warn("Display message pump has been waiting on the RFB main thread for over {} ms", STALL_WARN_NANOS / 1_000_000L);
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    public void beginPresent() {
        synchronized (lock) {
            presentInFlight = true;
            presentPumpPending = true;
            presentLoader = Thread.currentThread().getContextClassLoader();
        }
    }

    public void runPresentPump() {
        final ClassLoader loader;
        synchronized (lock) {
            loader = presentLoader;
        }
        final Throwable t = pumpWith(loader);
        synchronized (lock) {
            if (t != null) presentFailure = t;
            presentPumpPending = false;
            lock.notifyAll();
        }
    }

    public void endPresent() {
        synchronized (lock) {
            presentInFlight = false;
            presentPumpPending = false;
            lock.notifyAll();
        }
    }

    private void runPump() {
        final Throwable t = pumpWith(callerLoader);
        synchronized (lock) {
            failure = t;
            done = true;
            lock.notifyAll();
        }
    }

    private Throwable pumpWith(ClassLoader loader) {
        final Thread self = Thread.currentThread();
        final ClassLoader saved = self.getContextClassLoader();
        try {
            if (loader != null) self.setContextClassLoader(loader);
            pumpBody.run();
            return null;
        } catch (Throwable t) {
            return t;
        } finally {
            self.setContextClassLoader(saved);
        }
    }

    private static RuntimeException asUnchecked(Throwable t) {
        if (t instanceof RuntimeException runtime) return runtime;
        if (t instanceof Error error) throw error;
        return new RuntimeException(t);
    }
}
