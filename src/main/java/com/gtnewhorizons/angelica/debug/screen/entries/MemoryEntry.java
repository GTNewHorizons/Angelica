package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public final class MemoryEntry implements DebugScreenEntry {

    private static final String MIB = "MiB";

    static final class AllocationRate {

        private static final long MIN_INTERVAL_MS = 500L;
        private static final long MAX_INTERVAL_MS = 5000L;

        private long lastTime;
        private long lastHeapUsage = -1L;
        private long lastGcCounts = -1L;
        private long lastRate;

        long rate() {
            return lastRate;
        }

        void sample(long timeMs, long heapUsage, long gcCounts) {
            final long dt = timeMs - lastTime;
            if (lastTime != 0L && dt < MIN_INTERVAL_MS) return;
            if (lastTime != 0L && dt <= MAX_INTERVAL_MS && gcCounts == lastGcCounts) {
                lastRate = Math.round((heapUsage - lastHeapUsage) * (1000.0 / dt));
            }
            lastTime = timeMs;
            lastHeapUsage = heapUsage;
            lastGcCounts = gcCounts;
        }
    }

    private final AllocationRate allocationRate = new AllocationRate();
    private List<GarbageCollectorMXBean> collectors;
    private BufferPoolMXBean directPool;
    private boolean poolResolved;
    private long directBytes = -1L;

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        final Runtime runtime = Runtime.getRuntime();
        final long max = runtime.maxMemory();
        final long total = runtime.totalMemory();
        final long used = total - runtime.freeMemory();
        if (context.isSampleDue()) {
            allocationRate.sample(context.nanoTime() / 1_000_000L, used, gcCounts());
            if (!poolResolved) {
                poolResolved = true;
                directPool = findDirectPool();
            }
            if (directPool != null) directBytes = directPool.getMemoryUsed();
        }
        displayer.fact(DebugGroup.MEMORY, "Used").value(used * 100L / max).text("% ").padded(toMib(used), 3).text('/').padded(toMib(max), 3).text(MIB);
        displayer.fact(DebugGroup.MEMORY, "Allocated").value(total * 100L / max).text("% ").padded(toMib(total), 3).text(MIB);
        displayer.fact(DebugGroup.MEMORY, "Alloc rate").padded(toMib(allocationRate.rate()), 3).text(MIB).text("/s");
        if (directBytes >= 0L) {
            displayer.fact(DebugGroup.MEMORY, "Direct").padded(toMib(directBytes), 3).text(MIB);
        }
    }

    private long gcCounts() {
        if (collectors == null) collectors = ManagementFactory.getGarbageCollectorMXBeans();
        long sum = 0L;
        for (int i = 0, n = collectors.size(); i < n; i++) {
            sum += collectors.get(i).getCollectionCount();
        }
        return sum;
    }

    private static BufferPoolMXBean findDirectPool() {
        final List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        for (int i = 0, n = pools.size(); i < n; i++) {
            if (pools.get(i).getName().equals("direct")) return pools.get(i);
        }
        return null;
    }

    private static long toMib(long bytes) {
        return bytes / 1024L / 1024L;
    }
}
