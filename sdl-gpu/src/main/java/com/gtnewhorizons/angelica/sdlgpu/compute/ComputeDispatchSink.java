package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;

public interface ComputeDispatchSink {
    default long beginBatchedComputeDispatch(ContextState st) {
        return beginBatchedComputeDispatch(st, true);
    }

    long beginBatchedComputeDispatch(ContextState st, boolean cycleRwBuffers);

    void dispatchInBatch(long pass, int gx, int gy, int gz);

    void rebindRoStorageBuffers(ContextState st, long pass);

    void endBatchedComputeDispatch(long pass);

    void pushPendingComputeUniforms(ContextState st);
}
