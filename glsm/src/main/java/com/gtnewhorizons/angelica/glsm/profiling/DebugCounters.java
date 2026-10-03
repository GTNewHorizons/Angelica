package com.gtnewhorizons.angelica.glsm.profiling;

import java.util.concurrent.atomic.LongAdder;

public final class DebugCounters {
    public static final LongAdder FENCE_WAIT_NANOS = new LongAdder();
    public static final LongAdder TRANSFER_WAIT_NANOS = new LongAdder();
    public static final LongAdder PRESENTS = new LongAdder();
    public static final LongAdder EMPTY_ACQUIRES = new LongAdder();

    private DebugCounters() {}
}
