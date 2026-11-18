package com.gtnewhorizons.angelica.glsm.stacks;

public final class StackIdAllocator {

    private StackIdAllocator() {}

    private static int next = -1;
    private static int perContext = -1;

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

    public static int allocated() {
        return next;
    }
}
