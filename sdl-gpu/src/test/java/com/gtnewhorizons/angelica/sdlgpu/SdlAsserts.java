package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public final class SdlAsserts {

    private static final int WARMUP_ROUNDS = 50_000;
    private static final int MEASURED_ROUNDS = 100_000;
    private static final long ALLOCATION_BUDGET_BYTES = 256L * 1024L;

    private SdlAsserts() {}

    public static void assertBlendFactors(PipelineCache p, int drawBuffer, int sc, int dc, int sa, int da, String when) {
        assertEquals(sc, p.srcColorFactors[drawBuffer], when + ": srcColor of draw buffer " + drawBuffer);
        assertEquals(dc, p.dstColorFactors[drawBuffer], when + ": dstColor of draw buffer " + drawBuffer);
        assertEquals(sa, p.srcAlphaFactors[drawBuffer], when + ": srcAlpha of draw buffer " + drawBuffer);
        assertEquals(da, p.dstAlphaFactors[drawBuffer], when + ": dstAlpha of draw buffer " + drawBuffer);
    }

    public static void assertAllocationFree(Runnable round, String what) {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean);
        final ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled());
        for (int i = 0; i < WARMUP_ROUNDS; i++) round.run();
        final long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < MEASURED_ROUNDS; i++) round.run();
        final long allocated = bean.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < ALLOCATION_BUDGET_BYTES, allocated + " bytes allocated across " + MEASURED_ROUNDS + " rounds of " + what);
    }
}
