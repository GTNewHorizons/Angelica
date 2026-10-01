package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.TextureStaging;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.TransferThread;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlOutOfFrameExtension.class)
class GlsmSdlTextureStagingTest {

    private static final int STAGING_SIZE = 64;
    private static final int BIG_TEX = 2048;
    private static final int BIG_W = 1024;
    private static final int BIG_H = 1100;
    private static final int SMALL = 16;
    private static final int COLOR_A = 0xFF10E020;
    private static final int COLOR_B = 0xFF3050C0;

    private static TransferThread installed;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
        final Object backend = BackendManager.RENDER_BACKEND;
        if (Reflect.get(backend, "transferThread") == null) {
            final ResourceManager rm = Reflect.get(backend, "resourceManager");
            installed = new TransferThread(SDLGPUGate.device(), rm);
            Reflect.set(backend, "transferThread", installed);
            rm.setTransferThread(installed);
        }
    }

    @AfterAll
    static void removeTransferThread() throws InterruptedException {
        if (installed == null) return;
        GlsmSdlHeadlessRig.endFrame();
        installed.shutdown();
        installed.getThread().join();
        final Object backend = BackendManager.RENDER_BACKEND;
        Reflect.set(backend, "transferThread", null);
        final ResourceManager rm = Reflect.get(backend, "resourceManager");
        rm.setTransferThread(null);
        installed = null;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void stagedBandsRoundTripPerLevel(boolean inFrame) {
        if (inFrame) GlsmSdlHeadlessRig.beginFrame();
        final int tex = GlsmSdlHeadlessRig.createSolidMipTexture(STAGING_SIZE, 1, 0);
        for (int level = 0; level <= 1; level++) {
            final int size = STAGING_SIZE >> level;
            final int half = size / 2;
            stageBand(tex, level, 0, size, half);
            stageBand(tex, level, half, size, half);
        }
        for (int level = 0; level <= 1; level++) {
            final int size = STAGING_SIZE >> level;
            final int[] got = GlsmSdlHeadlessRig.readTextureLevel(tex, level);
            assertEquals(size * size, got.length, "level " + level + " size");
            int mismatches = 0;
            int first = -1;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (got[y * size + x] != stagedColor(level, x, y)) {
                        if (first < 0) first = y * size + x;
                        mismatches++;
                    }
                }
            }
            if (mismatches != 0) {
                final int fx = first % size;
                final int fy = first / size;
                fail("level " + level + ": " + mismatches + " texels differ; first (" + fx + "," + fy + ") expected " + GlsmSdlHeadlessRig.describe(stagedColor(level, fx, fy)) + " got " + GlsmSdlHeadlessRig.describe(got[first]));
            }
        }
        GLStateManager.glDeleteTextures(tex);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,true", "true,false"})
    void directUploadLandsAfterEarlierBatchedUpload(boolean inFrame, boolean fboAttached) {
        if (inFrame) GlsmSdlHeadlessRig.beginFrame();
        final int tex = GlsmSdlHeadlessRig.newNearestTexture();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, BIG_TEX, BIG_TEX, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        int fbo = 0;
        if (fboAttached) {
            fbo = GLStateManager.glGenFramebuffers();
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        }

        final ByteBuffer small = solidRgba(SMALL, SMALL, COLOR_A);
        final ByteBuffer big = solidRgba(BIG_W, BIG_H, COLOR_B);
        final ByteBuffer out = MemoryUtil.memAlloc(BIG_TEX * BIG_TEX * 4);
        try {
            assertTrue(big.remaining() > ResourceManager.BATCH_SEGMENT_CAPACITY, "second upload must take the direct path");
            GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SMALL, SMALL, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, small);
            GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, BIG_W, BIG_H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, big);

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, out);
            final int[] got = GlsmSdlHeadlessRig.toArgb(out, BIG_TEX * BIG_TEX);
            int mismatches = 0;
            for (int y = 0; y < BIG_H; y++) {
                for (int x = 0; x < BIG_W; x++) {
                    if (got[y * BIG_TEX + x] != COLOR_B) mismatches++;
                }
            }
            final int corner = got[0];
            assertEquals(0, mismatches, () -> "texels not holding the later upload; (0,0) is " + GlsmSdlHeadlessRig.describe(corner));
        } finally {
            MemoryUtil.memFree(small);
            MemoryUtil.memFree(big);
            MemoryUtil.memFree(out);
        }
        if (fbo != 0) GLStateManager.glDeleteFramebuffers(fbo);
        GLStateManager.glDeleteTextures(tex);
    }

    private static void stageBand(int tex, int level, int y0, int w, int h) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        final TextureStaging staging = GLStateManager.beginTextureStaging(level, 0, y0, w, h);
        assertNotNull(staging, "beginTextureStaging level " + level + " y " + y0);
        final ByteBuffer buf = staging.buffer();
        assertEquals(w * h * 4, buf.remaining(), "staging capacity");
        final boolean bgra = staging.bgra();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int c = stagedColor(level, x, y0 + y);
                final int i = (y * w + x) * 4;
                final byte r = (byte) (c >> 16);
                final byte b = (byte) c;
                buf.put(i, bgra ? b : r);
                buf.put(i + 1, (byte) (c >> 8));
                buf.put(i + 2, bgra ? r : b);
                buf.put(i + 3, (byte) (c >>> 24));
            }
        }
        assertTrue(GLStateManager.commitTextureStaging(staging), "commitTextureStaging level " + level + " y " + y0);
    }

    private static int stagedColor(int level, int x, int y) {
        return 0x80000000 | ((level * 0x55 + y) & 0x7F) << 24 | (x * 3 & 0xFF) << 16 | (y * 5 & 0xFF) << 8 | ((x ^ y) + level * 0x40) & 0xFF;
    }

    private static ByteBuffer solidRgba(int w, int h, int argb) {
        final ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
        for (int i = 0; i < w * h; i++) {
            buf.put(i * 4, (byte) (argb >> 16));
            buf.put(i * 4 + 1, (byte) (argb >> 8));
            buf.put(i * 4 + 2, (byte) argb);
            buf.put(i * 4 + 3, (byte) (argb >>> 24));
        }
        return buf;
    }
}
