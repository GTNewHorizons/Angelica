package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.client.renderer.CapturingTessellator;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import net.minecraft.client.renderer.Tessellator;

import java.util.List;

@SuppressWarnings("unused")
public class TessellatorManager {
    private static final ThreadLocal<CapturingTessellator> capturingTessellator = ThreadLocal.withInitial(CapturingTessellator::new);

    private static final ThreadLocal<Boolean> currentlyCapturing = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Tessellator> theTessellator = ThreadLocal.withInitial(Tessellator::new);
    private static final Thread mainThread = Thread.currentThread();

    public static Tessellator get() {
        if(currentlyCapturing.get()) {
            return capturingTessellator.get();
        } else if(isOnMainThread()) {
            return Tessellator.instance;
        }
        return theTessellator.get();
    }

    public static boolean isOnMainThread() {
        return Thread.currentThread() == mainThread;
    }

    public static boolean isMainInstance(Object instance) {
        return instance == Tessellator.instance || isOnMainThread();
    }

    public static void startCapturing() {
        if(currentlyCapturing.get()) throw new IllegalStateException("Tried to start capturing when already capturing!");
        currentlyCapturing.set(true);
    }
    public static List<Quad> stopCapturing() {
        if(!currentlyCapturing.get()) throw new IllegalStateException("Tried to stop capturing when not capturing!");
        currentlyCapturing.set(false);
        final CapturingTessellator tess = capturingTessellator.get();
        final List<Quad> quads = tess.getQuads();
        tess.reset();
        tess.resetQuadBuf();
        return quads;
    }

    static {
        System.out.println("[TessellatorManager] Initialized on thread " + mainThread.getName());
    }
}
