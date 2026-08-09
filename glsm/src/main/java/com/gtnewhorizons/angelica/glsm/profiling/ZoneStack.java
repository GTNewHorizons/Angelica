package com.gtnewhorizons.angelica.glsm.profiling;

public final class ZoneStack {
    public static final long EMPTY = -1L;
    static final int MAX_DEPTH = 512;

    private long[] stack = new long[64];
    private boolean[] gpu = new boolean[64];
    private String[] names = new String[64];
    private int depth;
    private int skipped;
    private boolean lastPoppedGpu;
    private String lastPoppedName;

    public boolean atCap() {
        return depth == MAX_DEPTH;
    }

    public boolean push(long ctx) {
        return push(ctx, false, null);
    }

    public boolean push(long ctx, boolean gpuBracketed) {
        return push(ctx, gpuBracketed, null);
    }

    public boolean push(long ctx, boolean gpuBracketed, String name) {
        if (depth == MAX_DEPTH) {
            skipped++;
            return false;
        }
        if (depth == stack.length) {
            final long[] grownStack = new long[stack.length * 2];
            System.arraycopy(stack, 0, grownStack, 0, stack.length);
            stack = grownStack;
            final boolean[] grownGpu = new boolean[gpu.length * 2];
            System.arraycopy(gpu, 0, grownGpu, 0, gpu.length);
            gpu = grownGpu;
            final String[] grownNames = new String[names.length * 2];
            System.arraycopy(names, 0, grownNames, 0, names.length);
            names = grownNames;
        }
        gpu[depth] = gpuBracketed;
        names[depth] = name;
        stack[depth++] = ctx;
        return true;
    }

    public long pop() {
        if (skipped > 0) {
            skipped--;
            lastPoppedGpu = false;
            lastPoppedName = null;
            return EMPTY;
        }
        if (depth == 0) {
            lastPoppedGpu = false;
            lastPoppedName = null;
            return EMPTY;
        }
        lastPoppedGpu = gpu[--depth];
        lastPoppedName = names[depth];
        names[depth] = null;
        return stack[depth];
    }

    public boolean poppedGpu() {
        return lastPoppedGpu;
    }

    public String poppedName() {
        return lastPoppedName;
    }

    public boolean hasSkipped() {
        return skipped > 0;
    }

    public long peek() {
        if (skipped > 0 || depth == 0) return EMPTY;
        return stack[depth - 1];
    }

    public int depth() {
        return depth;
    }
}
