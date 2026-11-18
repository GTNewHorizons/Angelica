package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;

public final class InvocationDispatch {
    static final int MAX_GROUPS_PER_DISPATCH = 65535;
    static final int WORKGROUP_SIZE = 64;

    @FunctionalInterface
    public interface IntUniformWriter {
        void write(ContextState st, int location, int value);
    }

    private InvocationDispatch() {}

    static void dispatch(ComputeDispatchSink sink, ContextState st, long pass, long invocations, int invocationBaseLocation, IntUniformWriter writer) {
        final long groups = (invocations + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
        for (long g0 = 0; g0 < groups; g0 += MAX_GROUPS_PER_DISPATCH) {
            if (invocationBaseLocation >= 0) writer.write(st, invocationBaseLocation, (int) (g0 * WORKGROUP_SIZE));
            sink.pushPendingComputeUniforms(st);
            sink.dispatchInBatch(pass, (int) Math.min(MAX_GROUPS_PER_DISPATCH, groups - g0), 1, 1);
        }
    }
}
