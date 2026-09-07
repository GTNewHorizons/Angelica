package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;

@Timeout(120)
class CopyTexSubImageFromFbo0GpuTest {

    private static final int SIZE = 64;
    private static final int TALL = 96;
    private static final int ROW_BYTES = SIZE * 4;

    private static Device device;
    private static FrameManager frameManager;
    private static ResourceManager resourceManager;
    private static TextureOps textureOps;
    private static int nextGlId = 1000;

    @BeforeAll
    static void setUp() throws Exception {
        final SdlTestRig rig = SdlTestRig.acquireRealDevice();
        device = rig.device;
        frameManager = rig.frameManager;
        resourceManager = rig.resourceManager;
        textureOps = new TextureOps(device, frameManager, resourceManager, new FBOClearTracker(frameManager, resourceManager, null));
    }

    @AfterAll
    static void tearDown() {
        if (frameManager != null && frameManager.isFrameActive()) frameManager.endFrame();
        if (frameManager != null) frameManager.destroyFinalTarget();
        SdlTestRig.releaseRealDevice();
    }

    private static byte srcRed(int col) {
        return (byte) (16 + col * 3);
    }

    private static byte srcGreen(int row) {
        return (byte) (7 + row * 3);
    }

    private static ByteBuffer fbo0Pattern(int height) {
        final ByteBuffer buf = MemoryUtil.memAlloc(SIZE * height * 4);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < SIZE; col++) {
                final int i = row * ROW_BYTES + col * 4;
                buf.put(i, srcRed(col));
                buf.put(i + 1, srcGreen(row));
                buf.put(i + 2, (byte) 0x40);
                buf.put(i + 3, (byte) 0xFF);
            }
        }
        return buf;
    }

    private static final byte[] SENTINEL = { 0x11, 0x22, 0x33, 0x44 };

    private static ByteBuffer sentinelPattern() {
        final ByteBuffer buf = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        for (int i = 0; i < SIZE * SIZE; i++) {
            for (int c = 0; c < 4; c++) buf.put(i * 4 + c, SENTINEL[c]);
        }
        return buf;
    }

    private static void upload(long texture, ByteBuffer data, int width, int height) {
        final long cp = frameManager.ensureCopyPass();
        assertNotEquals(0L, cp, "no copy pass");
        resourceManager.uploadToTexture(cp, data, texture, 0, 0, width, height, 0);
        resourceManager.flushBatchedUploads(cp);
        frameManager.endCopyPassIfActive();
    }

    private static int createDest() {
        final int glId = nextGlId++;
        final long handle = resourceManager.createTexture(glId, GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, SIZE, SIZE, 1, 1);
        assertNotEquals(0L, handle, "dest texture creation failed");
        return glId;
    }

    private static int createSentinelDest() {
        final int destGlId = createDest();
        final ByteBuffer sentinel = sentinelPattern();
        try {
            upload(resourceManager.getTextureHandle(destGlId), sentinel, SIZE, SIZE);
        } finally {
            MemoryUtil.memFree(sentinel);
        }
        return destGlId;
    }

    private static void prepareFbo0(int height) {
        frameManager.destroyFinalTarget();
        frameManager.finalTarget().create(device, resourceManager, SIZE, height, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM);
        assertEquals(SIZE, frameManager.getFbo0Width());
        assertEquals(height, frameManager.getFbo0Height());
        final ByteBuffer pattern = fbo0Pattern(height);
        try {
            upload(frameManager.getFbo0Texture(), pattern, SIZE, height);
        } finally {
            MemoryUtil.memFree(pattern);
        }
    }

    private static ByteBuffer download(int destGlId) {
        final ByteBuffer out = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        textureOps.readbackTexture(resourceManager.getTextureHandle(destGlId), 0, 0, SIZE, SIZE, 0, out);
        out.rewind();
        return out;
    }

    private static void assertPixel(ByteBuffer got, int col, int row, byte r, byte g, byte b, byte a, String what) {
        final int i = row * ROW_BYTES + col * 4;
        assertEquals(r, got.get(i), what + " R at col=" + col + " row=" + row);
        assertEquals(g, got.get(i + 1), what + " G at col=" + col + " row=" + row);
        assertEquals(b, got.get(i + 2), what + " B at col=" + col + " row=" + row);
        assertEquals(a, got.get(i + 3), what + " A at col=" + col + " row=" + row);
    }

    private static void assertCopiedRegion(ByteBuffer got, int fbo0Height, int dx, int dy, int sx, int sy, int w, int h, String what) {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                final boolean inside = col >= dx && col < dx + w && row >= dy && row < dy + h;
                if (inside) {
                    final int srcCol = sx + (col - dx);
                    final int srcStorageRow = fbo0Height - 1 - (sy + (row - dy));
                    assertPixel(got, col, row, srcRed(srcCol), srcGreen(srcStorageRow), (byte) 0x40, (byte) 0xFF, what);
                } else {
                    assertPixel(got, col, row, SENTINEL[0], SENTINEL[1], SENTINEL[2], SENTINEL[3], "outside " + what);
                }
            }
        }
    }

    @Test
    void fullCopyFromDefaultFramebufferIsFlippedIntoTheTexture() {
        frameManager.beginFrame();
        frameManager.frame().swapchainUnavailable = true;
        prepareFbo0(SIZE);

        final int destGlId = createSentinelDest();

        final ContextState st = new ContextState();
        st.boundReadFboId = 0;
        textureOps.copyTexSubImageImpl(st, destGlId, 0, 0, 0, 0, 0, SIZE, SIZE);

        final ByteBuffer got = download(destGlId);
        try {
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    assertPixel(got, col, row, srcRed(col), srcGreen(SIZE - 1 - row), (byte) 0x40, (byte) 0xFF, "full copy");
                }
            }
        } finally {
            MemoryUtil.memFree(got);
        }
        frameManager.endFrame();
    }

    @Test
    void subRectCopyFromATallerDefaultFramebufferUsesTheFullHeightBasis() {
        frameManager.beginFrame();
        frameManager.frame().swapchainUnavailable = true;
        prepareFbo0(TALL);

        final int destGlId = createSentinelDest();

        final ContextState st = new ContextState();
        st.boundReadFboId = 0;
        textureOps.copyTexSubImageImpl(st, destGlId, 0, 5, 9, 3, 2, 7, 4);

        final ByteBuffer got = download(destGlId);
        try {
            assertCopiedRegion(got, TALL, 5, 9, 3, 2, 7, 4, "tall sub-rect copy");
        } finally {
            MemoryUtil.memFree(got);
        }
        frameManager.endFrame();
    }
}
