package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;

public final class VoxelizationDispatcher {
    private static final InvocationDispatch.IntUniformWriter UNIFORM_1I = (st, location, value) -> GLStateManager.glUniform1i(location, value);

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
        InvocationDispatch.dispatch(computeBinder, st, pass, vertexTotal, locInvBase, UNIFORM_1I);
    }

    public void endBatch(long pass) {
        computeBinder.endBatchedComputeDispatch(pass);
    }
}
