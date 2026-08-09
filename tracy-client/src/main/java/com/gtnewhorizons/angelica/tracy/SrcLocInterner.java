package com.gtnewhorizons.angelica.tracy;

import java.util.concurrent.ConcurrentHashMap;

final class SrcLocInterner {
    interface NativeOps {
        long allocSrcLoc(String name, int color);
    }

    static final String DYNAMIC_NAME = "<dynamic>";

    private final ConcurrentHashMap<String, Long> interned = new ConcurrentHashMap<>();
    private final NativeOps ops;
    private final int cap;
    private final long dynamic;

    SrcLocInterner(NativeOps ops, int cap) {
        this.ops = ops;
        this.cap = cap;
        this.dynamic = ops.allocSrcLoc(DYNAMIC_NAME, 0);
    }

    long dynamicSrcLoc() {
        return dynamic;
    }

    long intern(String name, int color) {
        final Long existing = interned.get(name);
        if (existing != null) return existing;
        if (interned.size() >= cap) return dynamic;
        return interned.computeIfAbsent(name, n -> ops.allocSrcLoc(n, color));
    }

    int size() {
        return interned.size();
    }
}
