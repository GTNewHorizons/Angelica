package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FFPTraceTest {

    @Test
    void uboFloatReadIgnoresATruncatedLimit() {
        final ByteBuffer buf = MemoryUtil.memAlloc(64);
        try {
            for (int i = 0; i < 16; i++) buf.putFloat(i * 4, i + 0.5f);
            buf.position(0).limit(4);
            assertEquals(0.5f, readUboFloat(buf, 0));
            assertEquals(12.5f, readUboFloat(buf, 48));
            assertEquals(15.5f, readUboFloat(buf, 60));
            assertEquals(4, buf.limit());
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    private static float readUboFloat(ByteBuffer buf, int at) {
        return Reflect.invokeStatic(FFPTrace.class, "floatIgnoringLimit", new Class<?>[] { ByteBuffer.class, int.class }, buf, at);
    }
}
