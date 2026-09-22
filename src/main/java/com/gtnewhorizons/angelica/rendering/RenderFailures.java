package com.gtnewhorizons.angelica.rendering;

public final class RenderFailures {
    private RenderFailures() {}

    public static Throwable suppress(Throwable failure, Throwable cleanup) {
        if (failure == null) return cleanup;
        if (failure != cleanup) failure.addSuppressed(cleanup);
        return failure;
    }

    public static <T extends Throwable> void rethrow(Throwable failure) throws T {
        if (failure != null) throw (T) failure;
    }

    public static void rethrowWrapped(Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }
}
