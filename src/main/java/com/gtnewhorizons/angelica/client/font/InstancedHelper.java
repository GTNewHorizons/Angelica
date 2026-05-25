package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

public final class InstancedHelper {
    private static int quadVBO;

    public static int getQuadVBO() {
        if (quadVBO > 0) return quadVBO;
        quadVBO = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVBO);

        final ByteBuffer data = memAlloc(32);
        final long address = memAddress0(data);
        memPutFloat(address, 0); memPutFloat(address + 4, 0);
        memPutFloat(address + 8, 1); memPutFloat(address + 12, 0);
        memPutFloat(address + 16, 0); memPutFloat(address + 20, 1);
        memPutFloat(address + 24, 1); memPutFloat(address + 28, 1);

        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        memFree(data);

        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return quadVBO;
    }
}
