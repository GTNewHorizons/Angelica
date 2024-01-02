package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.client.renderer.CapturingTessellator;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
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

    public static ByteBuffer quadsToBuffer(List<Quad> quads, VertexFormat format) {
        final ByteBuffer byteBuffer = BufferUtils.createByteBuffer(format.getVertexSize() * quads.size() * 4);

        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            final Quad quad = quads.get(i);
            for (int idx = 0; idx < 4; idx++) {
                // Position
                byteBuffer.putFloat(quad.getX(idx));
                byteBuffer.putFloat(quad.getY(idx));
                byteBuffer.putFloat(quad.getZ(idx));

                // Texture
                byteBuffer.putFloat(quad.getTexU(idx));
                byteBuffer.putFloat(quad.getTexV(idx));

                // Normals
                byteBuffer.putInt(quad.getNormal(idx));
            }
        }
        byteBuffer.rewind();
        return byteBuffer;
    }

    static {
        System.out.println("[TessellatorManager] Initialized on thread " + mainThread.getName());
    }
}
