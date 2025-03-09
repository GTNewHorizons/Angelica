package org.embeddedt.embeddium.impl.render.chunk.compile;

import org.jetbrains.annotations.Nullable;

public final class GlobalChunkBuildContext {
    private static ChunkBuildContext mainThreadContext;
    private static Thread mainThread;

    private GlobalChunkBuildContext() {}

    public static void setMainThread() {
        mainThread = Thread.currentThread();
    }

    @Nullable
    public static ChunkBuildContext get() {
        var thread = Thread.currentThread();
        // Main thread first, because it's the most common case
        if(thread == mainThread) {
            return mainThreadContext;
        } else if(thread instanceof Holder holder) {
            return holder.embeddium$getGlobalContext();
        } else {
            return null;
        }
    }

    public static void bindMainThread(ChunkBuildContext context) {
        mainThreadContext = context;
    }

    public interface Holder {
        ChunkBuildContext embeddium$getGlobalContext();
    }
}
