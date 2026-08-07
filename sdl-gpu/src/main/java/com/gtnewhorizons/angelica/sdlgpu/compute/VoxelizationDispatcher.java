package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;

public final class VoxelizationDispatcher {
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

    public void dispatchRange(long pass, int locStart, int locCount, int vertexOffset, int vertexCount, ContextState st) {
        if (pass == 0 || vertexCount <= 0) return;
        if (locStart >= 0) GLStateManager.glUniform1i(locStart, vertexOffset);
        if (locCount >= 0) GLStateManager.glUniform1i(locCount, vertexCount);
        computeBinder.pushPendingComputeUniforms(st);
        final int groups = (vertexCount + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
        computeBinder.dispatchInBatch(pass, groups, 1, 1);
    }

    public void endBatch(long pass) {
        if (pass == 0) return;
        computeBinder.endBatchedComputeDispatch(pass);
    }

    public static int workgroupSize() { return WORKGROUP_SIZE; }
}
