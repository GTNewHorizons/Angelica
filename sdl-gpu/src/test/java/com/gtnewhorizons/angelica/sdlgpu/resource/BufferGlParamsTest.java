package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BufferGlParamsTest {

    @Test
    void unknownBuffer_reportsGlDefaults() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        assertEquals(GL15.GL_STATIC_DRAW, rm.getBufferGlUsage(4242));
        assertEquals(BufferParams.MUTABLE_STORE, rm.getBufferStorageFlags(4242));
        assertEquals(0L, rm.getBufferSize(4242));
    }

    @Test
    void recordedParams_roundTrip() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        rm.recordBufferGlParams(1, GL15.GL_STREAM_DRAW, BufferParams.MUTABLE_STORE);
        assertEquals(GL15.GL_STREAM_DRAW, rm.getBufferGlUsage(1));
        assertEquals(BufferParams.MUTABLE_STORE, rm.getBufferStorageFlags(1));

        final int flags = GL44.GL_MAP_PERSISTENT_BIT | GL30.GL_MAP_WRITE_BIT;
        rm.recordBufferGlParams(2, GL15.GL_DYNAMIC_DRAW, flags);
        assertEquals(GL15.GL_DYNAMIC_DRAW, rm.getBufferGlUsage(2));
        assertEquals(flags, rm.getBufferStorageFlags(2));
    }

    @Test
    void recordedParams_overwrittenOnRespecification() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        rm.recordBufferGlParams(3, GL15.GL_DYNAMIC_DRAW, GL30.GL_MAP_WRITE_BIT);
        rm.recordBufferGlParams(3, GL15.GL_STATIC_DRAW, BufferParams.MUTABLE_STORE);
        assertEquals(GL15.GL_STATIC_DRAW, rm.getBufferGlUsage(3));
        assertEquals(BufferParams.MUTABLE_STORE, rm.getBufferStorageFlags(3));
    }

    @Test
    void deleteBuffer_clearsParamsBackToDefaults() {
        final ResourceManager rm = SdlTestRig.resourceManager();
        rm.recordBufferSizeOnly(5, 1000);
        rm.recordBufferGlParams(5, GL15.GL_STREAM_READ, GL30.GL_MAP_READ_BIT);

        rm.deleteBuffer(5);

        assertEquals(GL15.GL_STATIC_DRAW, rm.getBufferGlUsage(5));
        assertEquals(BufferParams.MUTABLE_STORE, rm.getBufferStorageFlags(5));
        assertEquals(0L, rm.getBufferSize(5));
    }
}
