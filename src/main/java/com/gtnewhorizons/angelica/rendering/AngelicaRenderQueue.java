package com.gtnewhorizons.angelica.rendering;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public class AngelicaRenderQueue {
    private static final Thread MAIN_THREAD = Thread.currentThread();
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

    private static final Executor EXECUTOR = (runnable) -> {
        if(Thread.currentThread() == MAIN_THREAD) {
            runnable.run();
        } else {
            TASKS.add(runnable);
            LockSupport.unpark(MAIN_THREAD);
        }
    };

    public static Executor executor() {
        return EXECUTOR;
    }

    public static int processTasks(int max) {
        int tasksRun = 0;
        while(tasksRun < max) {
            Runnable r = TASKS.poll();
            if(r == null)
                break;
            r.run();
            tasksRun++;
        }
        return tasksRun;
    }

    public static int processTasks() {
        return processTasks(Integer.MAX_VALUE);
    }

    private static final long WAIT_TIME = TimeUnit.MILLISECONDS.toNanos(50);

    public static void managedBlock(BooleanSupplier isDone) {
        while(!isDone.getAsBoolean()) {
            if(AngelicaRenderQueue.processTasks(1) == 0) {
                LockSupport.parkNanos(WAIT_TIME);
            }
        }
    }
}
