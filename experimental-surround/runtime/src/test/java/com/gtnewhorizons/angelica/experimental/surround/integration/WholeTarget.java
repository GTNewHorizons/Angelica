package com.gtnewhorizons.angelica.experimental.surround.integration;

import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;

public class WholeTarget {

    public static boolean guard;

    public int compute(int a) {
        Trace.add("body " + a);
        return a * 2;
    }

    public void boom() {
        Trace.add("boom");
        throw new IllegalStateException("boom");
    }

    public void alpha() {
        Trace.add("alpha");
    }

    public void beta() {
        Trace.add("beta");
    }

    public synchronized int lockedInstance(int a) {
        Trace.add("body " + Thread.holdsLock(this));
        return a * 2;
    }

    public void run() {
        Trace.add("run");
        exit();
    }

    private void exit() {
        Trace.add("target exit");
    }

    public int priority(int a) {
        Trace.add("body " + a);
        return a;
    }

    public int branching(int a) {
        Trace.add("body " + a);
        return a * 2;
    }

    public void skippedVoid(int a) {
        Trace.add("skippedVoid " + a);
    }

    public int reentrant(int a) {
        Trace.add("reentrant " + a);
        return a + 1;
    }

    public String skipAndReturn(String a) {
        Trace.add("skipAndReturn " + a);
        return a + "!";
    }

    public static int entryCatches(int a) {
        Trace.add("entryCatches " + a);
        if (a < 0) {
            throw new IllegalStateException("negative " + a);
        }
        return a * 2;
    }

    public int throwingFinally(int a, int b) {
        return pick(a, b);
    }

    private static int pick(int a, int b) {
        if (a > b) {
            Trace.add("gt");
            return a;
        }
        if (a < b) {
            Trace.add("lt");
            return b;
        }
        Trace.add("eq");
        return 0;
    }

    public static double wideBody(double a, long b) {
        Trace.add("body");
        return a + b;
    }

    public void throwWide(int a) {
        thrown(a);
    }

    public void throwObject(int a) {
        thrown(a);
    }

    private static void thrown(int a) {
        Trace.add("body " + a);
        throw new IllegalStateException("boom " + a);
    }

    public void throwingEntry(int a) {
        Trace.add("body " + a);
    }

    public int caught(int a) {
        try {
            if (a < 0) {
                throw new IllegalArgumentException("neg");
            }
            return a;
        } catch (IllegalArgumentException e) {
            Trace.add("caught " + e.getMessage());
            return -1;
        } finally {
            Trace.add("inner-finally");
        }
    }

    public int carryThree(int a) {
        return carried(a);
    }

    public int carryUninitialized(int a) {
        return carried(a);
    }

    public int carryGuarded(int a) {
        return carried(a);
    }

    public int carrySplitAtEnd(int a) {
        return carried(a);
    }

    public int carryWideBeforeFrame(int a) {
        return carried(a);
    }

    public int carryArrays(int a) {
        return carried(a);
    }

    public int reused(int a) {
        return carried(a);
    }

    private static int carried(int a) {
        Trace.add("body " + a);
        return a * 2;
    }

    public static int risky(int a) {
        if (a < 0) {
            throw new IllegalArgumentException("neg");
        }
        return a * 3;
    }

    public static Object asObject(Object value) {
        return value;
    }
}
