package com.gtnewhorizons.angelica.glsm.stacks;

public final class StackIdAllocator {

    private StackIdAllocator() {}

    private static final int STATIC_BASE = 4096;
    private static final int STATIC_CAPACITY = 16;

    private static int next = -1;
    private static int perContext = -1;
    private static int staticNext;

    public static void beginContext() {
        next = 0;
    }

    public static void endContext() {
        if (perContext == -1) {
            perContext = next;
        } else if (next != perContext) {
            throw new IllegalStateException("stack id count changed across contexts: " + perContext + " -> " + next);
        }
        next = -1;
    }

    public static int nextId() {
        return next >= 0 ? next++ : -1;
    }

    public static int nextStaticId() {
        if (staticNext >= STATIC_CAPACITY) {
            throw new IllegalStateException("static stack id capacity exceeded");
        }
        return STATIC_BASE + staticNext++;
    }

    public static int capacity() {
        return STATIC_BASE + STATIC_CAPACITY;
    }
}
