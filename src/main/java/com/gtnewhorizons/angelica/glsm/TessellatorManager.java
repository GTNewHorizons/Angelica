package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.client.renderer.CapturingTessellator;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import net.minecraft.client.renderer.Tessellator;

import java.util.List;

@SuppressWarnings("unused")
public class TessellatorManager {
    private static CapturingTessellator capturingTessellator = null;

    private static final ThreadLocal<Tessellator> theTessellator = ThreadLocal.withInitial(Tessellator::new);
    private static final Thread mainThread = Thread.currentThread();

    public static Tessellator get() {
        if(isOnMainThread()) {
            return capturingTessellator != null ? capturingTessellator : Tessellator.instance;
        }
        return theTessellator.get();
    }

    public static boolean isOnMainThread() {
        return Thread.currentThread() == mainThread;
    }

    public static boolean isMainInstance(Object instance) {
        return instance == Tessellator.instance || isOnMainThread();
    }

    public static void startCapturing(CapturingTessellator tessellator) {
        capturingTessellator = tessellator;
    }
    public static List<Quad> stopCapturing() {
        if(capturingTessellator == null) throw new IllegalStateException("Tried to stop capturing when not capturing!");

        final List<Quad> quads = capturingTessellator.getQuads();
        capturingTessellator = null;
        return quads;
    }

    static {
        System.out.println("[TessellatorManager] Initialized on thread " + mainThread.getName());
    }
}
