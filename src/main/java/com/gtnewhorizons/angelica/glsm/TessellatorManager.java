package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.client.renderer.CapturingTessellator;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.mixins.interfaces.ITessellatorInstance;
import net.minecraft.client.renderer.Tessellator;

import java.nio.ByteBuffer;
import java.util.List;

@SuppressWarnings("unused")
public class TessellatorManager {
    private static final ThreadLocal<CapturingTessellator> capturingTessellator = ThreadLocal.withInitial(CapturingTessellator::new);

    private static final ThreadLocal<Boolean> currentlyCapturing = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Thread mainThread = Thread.currentThread();

    public static Tessellator get() {
        if(currentlyCapturing.get()) {
            return capturingTessellator.get();
        } else if(isOnMainThread()) {
            return Tessellator.instance;
        } else {
            // TODO: Verify this works correctly and nothing unexpected is grabbing a tessellator off the main thread
            // when not capturing
            throw new IllegalStateException("Tried to get the Tessellator off the main thread when not capturing!");
        }
    }

    public static boolean isOnMainThread() {
        return Thread.currentThread() == mainThread;
    }

    public static boolean isMainInstance(Object instance) {
        return instance == Tessellator.instance || isOnMainThread();
    }

    public static void startCapturing() {
        if(currentlyCapturing.get()) throw new IllegalStateException("Tried to start capturing when already capturing!");
        if(capturingTessellator.get().getQuads().size() > 0) throw new IllegalStateException("Tried to start capturing with existing collected Quads!");

        currentlyCapturing.set(true);
    }

    /*
     * Stop the CapturingTessellator and return the pooled quads.  The quads are valid until clearQuads() is
     *  called on the CapturingTesselator, which must be done before starting capturing again.
     */
    public static List<Quad> stopCapturingToPooledQuads() {
        if(!currentlyCapturing.get()) throw new IllegalStateException("Tried to stop capturing when not capturing!");
        currentlyCapturing.set(false);
        final CapturingTessellator tess = capturingTessellator.get();

        // Be sure we got all the quads
        if(tess.isDrawing) tess.draw();

        final List<Quad> quads = tess.getQuads();
        ((ITessellatorInstance)tess).discard();

        return quads;
    }

    /*
     * Stops the CapturingTessellator, stores the quads in a buffer (based on the VertexFormat provided),
     * and clears the quads.
     */
    public static ByteBuffer stopCapturingToBuffer(VertexFormat format) {
        final ByteBuffer buf = CapturingTessellator.quadsToBuffer(stopCapturingToPooledQuads(), format);
        clearQuads();
        return buf;
    }

    public static void clearQuads() {
        capturingTessellator.get().clearQuads();
    }

    static {
        System.out.println("[TessellatorManager] Initialized on thread " + mainThread.getName());
    }

    public static void cleanup() {
        // Ensure we've cleaned everything up
        if(currentlyCapturing.get()) {
            currentlyCapturing.set(false);
            final CapturingTessellator tess = capturingTessellator.get();
            ((ITessellatorInstance)tess).discard();
            tess.clearQuads();
        }
    }
}
