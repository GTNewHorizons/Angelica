package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GateRecorderTest {

    @Test
    void theLongestBlockWithinAFrameWinsRegardlessOfOrder() {
        final RenderBackend.GateRecorder longestFirst = new RenderBackend.GateRecorder();
        longestFirst.recordGate(5_000_000L, 100L);
        longestFirst.recordGate(1_000_000L, 200L);

        final RenderBackend.GateSample out = new RenderBackend.GateSample();
        longestFirst.readGateSample(out);
        assertEquals(5_000_000L, out.durationNanos);
        assertEquals(100L, out.endNanos);

        final RenderBackend.GateRecorder shortestFirst = new RenderBackend.GateRecorder();
        shortestFirst.recordGate(1_000_000L, 10L);
        shortestFirst.recordGate(4_000_000L, 20L);

        shortestFirst.readGateSample(out);
        assertEquals(4_000_000L, out.durationNanos);
        assertEquals(20L, out.endNanos);
    }

    @Test
    void aReadConsumesTheSample() {
        final RenderBackend.GateRecorder recorder = new RenderBackend.GateRecorder();
        recorder.recordGate(2_000_000L, 50L);

        final RenderBackend.GateSample out = new RenderBackend.GateSample();
        recorder.readGateSample(out);

        recorder.readGateSample(out);
        assertEquals(0L, out.endNanos);
        assertEquals(0L, out.durationNanos);
    }

    @Test
    void gpuWaitAccumulatesAndResetsOnRead() {
        final RenderBackend.GateRecorder recorder = new RenderBackend.GateRecorder();
        recorder.recordGate(1_000_000L, 10L);
        recorder.recordGpuWait(300L);
        recorder.recordGpuWait(700L);

        final RenderBackend.GateSample out = new RenderBackend.GateSample();
        recorder.readGateSample(out);
        assertEquals(1_000L, out.gpuWaitNanos);

        recorder.recordGate(1_000_000L, 11L);
        recorder.readGateSample(out);
        assertEquals(0L, out.gpuWaitNanos);
    }

    @Test
    void gpuWaitIsDrainedOnASampleFreeRead() {
        final RenderBackend.GateRecorder recorder = new RenderBackend.GateRecorder();
        recorder.recordGpuWait(450L);

        final RenderBackend.GateSample out = new RenderBackend.GateSample();
        recorder.readGateSample(out);

        assertEquals(0L, out.endNanos);
        assertEquals(450L, out.gpuWaitNanos);

        recorder.readGateSample(out);
        assertEquals(0L, out.gpuWaitNanos);
    }
}
