package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COLOR_TARGET;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_SAMPLER;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

@Timeout(60)
class ReadbackSubmitOrderGpuTest {

    private static final int SIZE = 4;

    @AfterAll
    static void releaseDevice() {
        SdlTestRig.releaseRealDevice();
    }

    @Test
    void aReadbackSubmitsTheFramesWritesFirst() throws Exception {
        final SdlTestRig rig = SdlTestRig.acquireRealDevice();
        final ResourceManager rm = rig.resourceManager;
        final FrameManager fm = rig.frameManager;
        final TextureOps textureOps = new TextureOps(rig.device, fm, rm, null);

        final int glId = rm.genTexture();
        final long texture = rm.createTexture(glId, GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, SIZE, SIZE, 1, 1, SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);
        assertNotEquals(0L, texture, "texture creation failed");

        fm.beginFrame();
        final FrameManager.FrameState f = fm.frame();
        final long frameCb = f.commandBuffer;
        assertNotEquals(0L, frameCb, "no frame command buffer to order against");
        assertEquals(0, f.midFrameSubmitsThisFrame);

        final ByteBuffer out = memAlloc(SIZE * SIZE * 4);
        try {
            textureOps.readbackTexture(texture, 0, 0, SIZE, SIZE, 0, out);
        } finally {
            memFree(out);
        }

        assertEquals(1, f.midFrameSubmitsThisFrame, "the frame command buffer must be submitted before the download buffer, or the readback sees stale contents");
        assertNotEquals(frameCb, f.commandBuffer, "the frame must continue on a freshly acquired command buffer");

        fm.endFrame();
    }
}
