package com.gtnewhorizons.angelica.sdlgpu.device;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

public final class FenceReleaser {
    private final LongPredicate signaled;
    private final LongConsumer releaser;
    private final LongArrayList pending = new LongArrayList();

    FenceReleaser(LongPredicate signaled, LongConsumer releaser) {
        this.signaled = signaled;
        this.releaser = releaser;
    }

    public synchronized void release(long fence) {
        if (fence == 0) return;
        if (signaled.test(fence)) {
            releaser.accept(fence);
        } else {
            pending.add(fence);
        }
    }

    public synchronized void drain() {
        for (int i = pending.size() - 1; i >= 0; i--) {
            final long fence = pending.getLong(i);
            if (!signaled.test(fence)) continue;
            releaser.accept(fence);
            final long last = pending.popLong();
            if (i < pending.size()) pending.set(i, last);
        }
    }

    public synchronized void releaseAll() {
        for (int i = 0; i < pending.size(); i++) {
            releaser.accept(pending.getLong(i));
        }
        pending.clear();
    }
}
