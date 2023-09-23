package org.embeddedt.archaicfix.occlusion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.archaicfix.occlusion.util.IntStack;

public class OcclusionHelpers {
    public static OcclusionWorker worker;
    public static OcclusionRenderer renderer;
    public static long chunkUpdateDeadline;
    public static float partialTickTime;

    public static final boolean DEBUG_ALWAYS_RUN_OCCLUSION = Boolean.parseBoolean(System.getProperty("archaicfix.debug.alwaysRunOcclusion", "false"));
    public static final boolean DEBUG_PRINT_QUEUE_ITERATIONS = Boolean.parseBoolean(System.getProperty("archaicfix.debug.printQueueIterations", "false"));
    /** Update up to 1 chunk per frame when the framerate is uncapped, vanilla-style. */
    public static final boolean DEBUG_LAZY_CHUNK_UPDATES = Boolean.parseBoolean(System.getProperty("archaicfix.debug.lazyChunkUpdates", "false"));
    /** Disable speeding up chunk updates when the camera is static. */
    public static final boolean DEBUG_NO_UPDATE_ACCELERATION = Boolean.parseBoolean(System.getProperty("archaicfix.debug.noUpdateAcceleration", "false"));

    public static void init() {
        worker = new OcclusionWorker();
    }

    public static IntStack deferredAreas = new IntStack(6 * 1024);

    public static synchronized void updateArea(int x, int y, int z, int x2, int y2, int z2) {

        // backwards so it's more logical to extract
        deferredAreas.add(z2);
        deferredAreas.add(y2);
        deferredAreas.add(x2);
        deferredAreas.add(z);
        deferredAreas.add(y);
        deferredAreas.add(x);
    }

    public static synchronized void processUpdate(OcclusionRenderer render) {
        if (deferredAreas.isEmpty()) {
            return; // guard against multiple instances (no compatibility with mods that do this to us)
        }

        int x = deferredAreas.pop(), y = deferredAreas.pop(), z = deferredAreas.pop();
        int x2 = deferredAreas.pop(), y2 = deferredAreas.pop(), z2 = deferredAreas.pop();
        render.internalMarkBlockUpdate(x, y, z, x2, y2, z2);
    }
}
