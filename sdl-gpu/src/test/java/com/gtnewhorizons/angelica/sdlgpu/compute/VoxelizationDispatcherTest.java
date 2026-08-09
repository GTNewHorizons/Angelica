package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelizationDispatcherTest {

    private static final long PASS_HANDLE = 0xDEADBEEFL;

    private static final class RecordingSink implements ComputeDispatchSink {
        int beginCount;
        int endCount;
        int uniformPushCount;
        int rebindCount;
        boolean lastCycleRwBuffers = true;
        final List<int[]> dispatches = new ArrayList<>();

        @Override public long beginBatchedComputeDispatch(ContextState st, boolean cycleRwBuffers) {
            beginCount++;
            lastCycleRwBuffers = cycleRwBuffers;
            return PASS_HANDLE;
        }
        @Override public void dispatchInBatch(long pass, int gx, int gy, int gz) { dispatches.add(new int[]{gx, gy, gz}); }
        @Override public void rebindRoStorageBuffers(ContextState st, long pass) { rebindCount++; }
        @Override public void endBatchedComputeDispatch(long pass) { endCount++; }
        @Override public void pushPendingComputeUniforms(ContextState st) { uniformPushCount++; }
    }

    @Test
    void beginBatch_opensPassWithCycleFalse() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        assertEquals(PASS_HANDLE, d.beginBatch(new ContextState()));
        assertEquals(1, sink.beginCount);
        assertFalse(sink.lastCycleRwBuffers, "voxelization must open the batched pass with cycle=false on rw SSBO bindings");
    }

    @Test
    void dispatchRange_pushesUniformsBeforeEachDispatch() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        final ContextState st = new ContextState();
        d.dispatchRange(PASS_HANDLE, -1, -1, 0, 64, st);
        d.dispatchRange(PASS_HANDLE, -1, -1, 64, 64, st);
        assertEquals(2, sink.dispatches.size());
        assertTrue(sink.uniformPushCount >= sink.dispatches.size(), "pushPendingComputeUniforms must run at least once per dispatchInBatch so per-range uniforms land; pushes=" + sink.uniformPushCount + " dispatches=" + sink.dispatches.size());
    }

    @Test
    void dispatchRange_roundsGroupsUpToWorkgroupSize() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        final ContextState st = new ContextState();
        d.dispatchRange(PASS_HANDLE, -1, -1, 0, 1, st);
        d.dispatchRange(PASS_HANDLE, -1, -1, 0, 64, st);
        d.dispatchRange(PASS_HANDLE, -1, -1, 0, 65, st);
        assertEquals(1, sink.dispatches.get(0)[0]);
        assertEquals(1, sink.dispatches.get(1)[0]);
        assertEquals(2, sink.dispatches.get(2)[0]);
        for (int[] groups : sink.dispatches) {
            assertEquals(1, groups[1]);
            assertEquals(1, groups[2]);
        }
    }

    @Test
    void dispatchRange_ignoresEmptyRangeAndNullPass() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        final ContextState st = new ContextState();
        d.dispatchRange(PASS_HANDLE, -1, -1, 0, 0, st);
        d.dispatchRange(0, -1, -1, 0, 64, st);
        assertEquals(0, sink.dispatches.size());
        assertEquals(0, sink.uniformPushCount);
    }

    @Test
    void endBatch_ignoresNullPass() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        d.endBatch(0);
        assertEquals(0, sink.endCount);
        d.endBatch(PASS_HANDLE);
        assertEquals(1, sink.endCount);
    }

    @Test
    void rebindVertexBuffer_forwardsToTheOpenPass() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        d.rebindVertexBuffer(new ContextState(), PASS_HANDLE);
        assertEquals(1, sink.rebindCount, "a region change inside one encoder must rebind the vertex buffer");
        assertEquals(0, sink.beginCount, "rebinding must not open a second pass");
    }

    @Test
    void constants_matchExpectations() {
        assertEquals(64, VoxelizationDispatcher.workgroupSize());
    }
}
