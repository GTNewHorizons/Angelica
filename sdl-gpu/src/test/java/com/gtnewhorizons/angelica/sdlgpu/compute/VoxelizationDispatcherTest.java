package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    private static void region(VoxelizationDispatcher d, int rangeCount, int vertexTotal, ContextState st) {
        d.dispatchRegion(PASS_HANDLE, -1, -1, -1, -1, 0, rangeCount, vertexTotal, st);
    }

    @Test
    void dispatchRegion_pushesUniformsBeforeEachDispatch() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        final ContextState st = new ContextState();
        region(d, 1, 64, st);
        region(d, 3, 200, st);
        assertEquals(2, sink.dispatches.size());
        assertEquals(sink.dispatches.size(), sink.uniformPushCount);
    }

    @Test
    void dispatchRegion_roundsGroupsUpToWorkgroupSize() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        final ContextState st = new ContextState();
        region(d, 1, 1, st);
        region(d, 1, 64, st);
        region(d, 1, 65, st);
        assertEquals(1, sink.dispatches.get(0)[0]);
        assertEquals(1, sink.dispatches.get(1)[0]);
        assertEquals(2, sink.dispatches.get(2)[0]);
        for (int[] groups : sink.dispatches) {
            assertEquals(1, groups[1]);
            assertEquals(1, groups[2]);
        }
    }

    @Test
    void dispatchRegion_splitsAboveMaxGroups() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        region(d, 1, VoxelizationDispatcher.MAX_GROUPS_PER_DISPATCH * 64 + 1, new ContextState());
        assertEquals(2, sink.dispatches.size());
        assertEquals(VoxelizationDispatcher.MAX_GROUPS_PER_DISPATCH, sink.dispatches.get(0)[0]);
        assertEquals(1, sink.dispatches.get(1)[0]);
        assertEquals(2, sink.uniformPushCount);
    }

    @Test
    void rebindVertexBuffer_forwardsToTheOpenPass() {
        final RecordingSink sink = new RecordingSink();
        final VoxelizationDispatcher d = new VoxelizationDispatcher(sink);
        d.rebindVertexBuffer(new ContextState(), PASS_HANDLE);
        assertEquals(1, sink.rebindCount, "a region change inside one encoder must rebind the vertex buffer");
        assertEquals(0, sink.beginCount, "rebinding must not open a second pass");
    }
}
