package com.gtnewhorizons.angelica.utils;

import java.util.concurrent.Callable;

/**
 * Both {@link Runnable} and {@link Callable} are meant for off-thread/concurrent execution. This is just a callback, no
 * more, no less.
 */
@FunctionalInterface
public interface Callback {

    void run();
}
