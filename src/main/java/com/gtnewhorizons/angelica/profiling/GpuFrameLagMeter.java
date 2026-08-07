package com.gtnewhorizons.angelica.profiling;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import org.lwjgl.opengl.GL32;

public final class GpuFrameLagMeter {
    private static final int RING = 8;
    private static final long[] syncs = new long[RING];
    private static final long[] submitNs = new long[RING];
    private static int head;
    private static int tail;
    private static final long P_GPU_LAG_NS = Tracy.plotHandle("gpuLagNs");

    private GpuFrameLagMeter() {}

    public static void onFrame() {
        if (!Tracy.ENABLED) return;
        final RenderBackend rb = BackendManager.RENDER_BACKEND;
        while (tail != head) {
            final int slot = tail % RING;
            final int status = rb.clientWaitSync(syncs[slot], 0, 0);
            if (status != GL32.GL_ALREADY_SIGNALED && status != GL32.GL_CONDITION_SATISFIED) {
                if (status == GL32.GL_WAIT_FAILED) {
                    rb.deleteSync(syncs[slot]);
                    tail++;
                    continue;
                }
                break;
            }
            Tracy.plotInt(P_GPU_LAG_NS, System.nanoTime() - submitNs[slot]);
            rb.deleteSync(syncs[slot]);
            tail++;
        }
        if (head - tail < RING) {
            final long sync = rb.fenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (sync != 0) {
                syncs[head % RING] = sync;
                submitNs[head % RING] = System.nanoTime();
                head++;
            }
        }
    }
}
