package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;

public final class VoxelizationDispatcher {
    static final int MAX_GROUPS_PER_DISPATCH = 65535;
    private static final int WORKGROUP_SIZE = 64;

    private final ComputeDispatchSink computeBinder;

    public VoxelizationDispatcher(ComputeDispatchSink computeBinder) {
        this.computeBinder = computeBinder;
    }

    public long beginBatch(ContextState st) {
        return computeBinder.beginBatchedComputeDispatch(st, false);
    }

    public void rebindVertexBuffer(ContextState st, long pass) {
        computeBinder.rebindRoStorageBuffers(st, pass);
    }

    public void dispatchRegion(long pass, int locBase, int locCount, int locTotal, int locInvBase, int rangeBase, int rangeCount, int vertexTotal, ContextState st) {
        if (locBase >= 0) GLStateManager.glUniform1i(locBase, rangeBase);
        if (locCount >= 0) GLStateManager.glUniform1i(locCount, rangeCount);
        if (locTotal >= 0) GLStateManager.glUniform1i(locTotal, vertexTotal);
        final int groups = (vertexTotal + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
        for (int g0 = 0; g0 < groups; g0 += MAX_GROUPS_PER_DISPATCH) {
            if (locInvBase >= 0) GLStateManager.glUniform1i(locInvBase, g0 * WORKGROUP_SIZE);
            computeBinder.pushPendingComputeUniforms(st);
            computeBinder.dispatchInBatch(pass, Math.min(MAX_GROUPS_PER_DISPATCH, groups - g0), 1, 1);
        }
    }

    public void endBatch(long pass) {
        computeBinder.endBatchedComputeDispatch(pass);
    }
}
