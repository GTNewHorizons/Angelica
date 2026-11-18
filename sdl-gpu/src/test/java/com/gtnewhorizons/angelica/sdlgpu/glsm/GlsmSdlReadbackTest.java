package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.ScreenCapture;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.frame.FenceTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.ReadbackShadows;
import com.gtnewhorizons.angelica.sdlgpu.resource.SdlReflect;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlReadbackTest {

    private static final int TEX_SIZE = 8;
    private static final int R = 0x12;
    private static final int G = 0x34;
    private static final int B = 0x56;
    private static final int A = 0xFF;

    private static final int SRC_W = 64;
    private static final int SRC_H = 32;
    private static final int CAPTURE_W = 16;
    private static final int CAPTURE_H = 8;
    private static final int COPY_BYTES = 64;

    private record StreamReadCopy(int src, int dst, ByteBuffer data) {}

    @Test
    void getTexImageSeesDeferredTextureUpload() {
        final int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        final ByteBuffer src = solidTexture(TEX_SIZE, TEX_SIZE);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEX_SIZE, TEX_SIZE, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, src);

        final ByteBuffer dst = BufferUtils.createByteBuffer(TEX_SIZE * TEX_SIZE * 4);
        GLSMTextures.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, dst);

        assertPixels(dst, TEX_SIZE * TEX_SIZE);
        GLSMTextures.glDeleteTextures(texture);
    }

    @Test
    void getBufferSubDataSeesSameFrameStorageBufferWrite() {
        final int size = 64;
        final int ssbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);

        final ByteBuffer initial = BufferUtils.createByteBuffer(size);
        final ByteBuffer update = BufferUtils.createByteBuffer(size);
        for (int i = 0; i < size; i++) {
            initial.put(i, (byte) 0x11);
            update.put(i, (byte) (i + 1));
        }
        GLSMBuffers.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, initial, GL15.GL_DYNAMIC_DRAW);
        GLSMBuffers.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, update);

        final ByteBuffer dst = BufferUtils.createByteBuffer(size);
        GLSMBuffers.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, dst);

        for (int i = 0; i < size; i++) {
            assertEquals((byte) (i + 1), dst.get(i), "byte " + i + " should hold the same-frame sub-data write");
        }
        GLSMBuffers.glDeleteBuffers(ssbo);
    }

    @Test
    void readPixelsSeesPriorDraw() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GLSMFramebuffer.glReadPixels(
            GlsmSdlHeadlessRig.SIZE / 2, GlsmSdlHeadlessRig.SIZE / 2, 1, 1,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);

        assertEquals(0xFF, pixel.get(0) & 0xFF);
        assertEquals(0x00, pixel.get(1) & 0xFF);
        assertEquals(0x00, pixel.get(2) & 0xFF);
        assertEquals(0xFF, pixel.get(3) & 0xFF);
    }

    @Test
    void screenCaptureSeesUserFramebuffer() {
        final int texture = GlsmSdlHeadlessRig.newNearestTexture();
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SRC_W, SRC_H, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        final int framebuffer = GlsmSdlHeadlessRig.fboWithColor(texture, 0);
        GlTestSupport.fillQuadrants(SRC_W, SRC_H);
        final ScreenCapture capture = GLSMFramebuffer.createScreenCapture(CAPTURE_W, CAPTURE_H);
        try {
            assertTrue(capture.begin(framebuffer, SRC_W, SRC_H, 7L), "capture was not recorded");
            GlsmSdlHeadlessRig.endFrame();
            GlsmSdlHeadlessRig.beginFrame();
            awaitPoll(capture);
            assertEquals(7L, capture.tag());
            GlTestSupport.assertQuadrantsBottomUp(capture.address(), CAPTURE_W, CAPTURE_H);
            capture.release();

            final long texture0 = Reflect.<long[]>get(capture, "textures")[0];
            final long transferBuffer0 = Reflect.<long[]>get(capture, "transferBuffers")[0];
            assertNotEquals(0L, texture0, "slot 0 was never created");
            assertNotEquals(0L, transferBuffer0, "slot 0 was never created");
            capture.delete();
            GlsmSdlHeadlessRig.endFrame();
            GlsmSdlHeadlessRig.beginFrame();
            assertNotLive(texture0, "liveTextureHandles");
            assertNotLive(transferBuffer0, "liveTransferBufferHandles");
        } finally {
            capture.delete();
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLSMFramebuffer.glDeleteFramebuffers(framebuffer);
            GLSMTextures.glDeleteTextures(texture);
        }
    }

    private static void assertNotLive(long handle, String field) {
        final LongOpenHashSet live = Reflect.get(GlsmSdlHeadlessRig.resourceManager(), field);
        assertFalse(live.contains(handle), field + " still holds capture handle " + handle + " after delete");
    }

    @Test
    void deleteSyncBeforeSubmitIsNotResurrected() {
        final long sync = GLSMBuffers.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        GLSMBuffers.glDeleteSync(sync);
        GlsmSdlHeadlessRig.endFrame();
        GlsmSdlHeadlessRig.beginFrame();

        final FenceTracker fences = Reflect.get(BackendManager.RENDER_BACKEND, "fenceTracker");
        final Long2LongOpenHashMap fenceMap = Reflect.get(fences, "fenceMap");
        assertFalse(fenceMap.containsKey(sync), "a sync deleted before its submit must not be bound to the frame fence afterwards");
    }

    private static void awaitPoll(ScreenCapture capture) {
        final long deadline = System.nanoTime() + 5_000_000_000L;
        while (!capture.poll()) {
            assertTrue(System.nanoTime() < deadline, "capture did not complete within 5 s");
            LockSupport.parkNanos(1_000_000L);
        }
    }

    @Test
    void streamReadCopyServesWithoutAppFence() {
        GlsmSdlHeadlessRig.endFrame();
        final StreamReadCopy copy = streamReadCopy(false);
        assertTrue(hasShadow(copy.dst()), "copy into a GL_STREAM_READ buffer must take the shadow path");
        final ReadbackShadows shadows = Reflect.get(BackendManager.RENDER_BACKEND, "readbackShadows");
        assertNotEquals(0L, SdlReflect.readbackSlotSync(shadows, copy.dst(), 0), "the shadow download must carry a fence");

        final ByteBuffer read = BufferUtils.createByteBuffer(COPY_BYTES);
        GLSMBuffers.glGetBufferSubData(GL31.GL_COPY_WRITE_BUFFER, 0, read);

        assertBytes(copy.data(), read, "glGetBufferSubData right after the copy");
        assertEquals(0L, SdlReflect.readbackSlotSync(shadows, copy.dst(), 0), "the read must be served from the shadow, which consumes its fence");
        GLSMBuffers.glDeleteBuffers(copy.src());
        GLSMBuffers.glDeleteBuffers(copy.dst());
    }

    @Test
    void streamReadShadowSkippedWhileBoundAsSsbo() {
        GlsmSdlHeadlessRig.endFrame();
        final StreamReadCopy copy = streamReadCopy(true);
        assertFalse(hasShadow(copy.dst()), "a destination bound as an SSBO can be written by the GPU, so it must not be shadowed");

        final ByteBuffer read = BufferUtils.createByteBuffer(COPY_BYTES);
        GLSMBuffers.glGetBufferSubData(GL31.GL_COPY_WRITE_BUFFER, 0, read);
        assertBytes(copy.data(), read, "glGetBufferSubData of an SSBO-bound copy destination");
        GLSMBuffers.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, 0);
        GLSMBuffers.glDeleteBuffers(copy.src());
        GLSMBuffers.glDeleteBuffers(copy.dst());
    }

    @Test
    void streamReadCopyVisibleToMapAndGpu() {
        GlsmSdlHeadlessRig.endFrame();
        final StreamReadCopy copy = streamReadCopy(false);

        final ByteBuffer mapped = GLSMBuffers.glMapBufferRange(GL31.GL_COPY_WRITE_BUFFER, 0, COPY_BYTES, GL30.GL_MAP_READ_BIT);
        assertNotNull(mapped, "glMapBufferRange returned null");
        assertBytes(copy.data(), mapped, "glMapBufferRange READ of the copy destination");
        GLSMBuffers.glUnmapBuffer(GL31.GL_COPY_WRITE_BUFFER);

        final int third = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL31.GL_COPY_READ_BUFFER, copy.dst());
        GLSMBuffers.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, third);
        GLSMBuffers.glBufferData(GL31.GL_COPY_WRITE_BUFFER, COPY_BYTES, GL15.GL_DYNAMIC_DRAW);
        GLSMBuffers.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, COPY_BYTES);

        final ByteBuffer read = BufferUtils.createByteBuffer(COPY_BYTES);
        GLSMBuffers.glGetBufferSubData(GL31.GL_COPY_WRITE_BUFFER, 0, read);
        assertBytes(copy.data(), read, "GPU copy out of the copy destination");

        GLSMBuffers.glDeleteBuffers(third);
        GLSMBuffers.glDeleteBuffers(copy.src());
        GLSMBuffers.glDeleteBuffers(copy.dst());
    }

    @Test
    void streamReadShadowDroppedOnSubData() {
        GlsmSdlHeadlessRig.endFrame();
        final StreamReadCopy copy = streamReadCopy(false);

        final ByteBuffer other = BufferUtils.createByteBuffer(COPY_BYTES);
        for (int i = 0; i < COPY_BYTES; i++) other.put(i, (byte) (0xA0 ^ i));
        GLSMBuffers.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER, 0, other);

        final ByteBuffer read = BufferUtils.createByteBuffer(COPY_BYTES);
        GLSMBuffers.glGetBufferSubData(GL31.GL_COPY_WRITE_BUFFER, 0, read);

        assertBytes(other, read, "glGetBufferSubData after glBufferSubData over the copy");
        GLSMBuffers.glDeleteBuffers(copy.src());
        GLSMBuffers.glDeleteBuffers(copy.dst());
    }

    @Test
    void streamReadShadowServesOnlyDownloadedBytes() {
        GlsmSdlHeadlessRig.endFrame();
        final StreamReadCopy copy = streamReadCopy(false);
        final int half = COPY_BYTES / 2;
        final int quarter = COPY_BYTES / 4;

        final ByteBuffer tail = BufferUtils.createByteBuffer(half);
        for (int i = 0; i < half; i++) tail.put(i, (byte) (0x50 + i));
        GLSMBuffers.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER, half, tail);

        final ByteBuffer head = BufferUtils.createByteBuffer(quarter);
        for (int i = 0; i < quarter; i++) head.put(i, (byte) (0xC0 + i));
        final int src2 = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, src2);
        GLSMBuffers.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, head, GL15.GL_DYNAMIC_DRAW);
        GLSMBuffers.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src2);
        GLSMBuffers.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, quarter);

        final ByteBuffer read = BufferUtils.createByteBuffer(COPY_BYTES);
        GLSMBuffers.glGetBufferSubData(GL31.GL_COPY_WRITE_BUFFER, 0, read);

        for (int i = 0; i < COPY_BYTES; i++) {
            final byte expected = i < quarter ? head.get(i) : i < half ? copy.data().get(i) : tail.get(i - half);
            assertEquals(expected, read.get(i), "byte " + i + " after a shorter copy reused a larger shadow");
        }
        GLSMBuffers.glDeleteBuffers(src2);
        GLSMBuffers.glDeleteBuffers(copy.src());
        GLSMBuffers.glDeleteBuffers(copy.dst());
    }

    private static StreamReadCopy streamReadCopy(boolean dstBoundAsSsbo) {
        final ByteBuffer data = BufferUtils.createByteBuffer(COPY_BYTES);
        for (int i = 0; i < COPY_BYTES; i++) data.put(i, (byte) (i * 7 + 3));

        final int src = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, src);
        GLSMBuffers.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_DRAW);

        final int dst = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src);
        GLSMBuffers.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dst);
        GLSMBuffers.glBufferData(GL31.GL_COPY_WRITE_BUFFER, COPY_BYTES, GL15.GL_STREAM_READ);
        if (dstBoundAsSsbo) GLSMBuffers.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, dst);
        GLSMBuffers.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, 0, 0, COPY_BYTES);
        return new StreamReadCopy(src, dst, data);
    }

    private static boolean hasShadow(int glId) {
        final ReadbackShadows shadows = Reflect.get(BackendManager.RENDER_BACKEND, "readbackShadows");
        return shadows.hasSlotsFor(glId);
    }

    private static void assertBytes(ByteBuffer expected, ByteBuffer actual, String what) {
        for (int i = 0; i < COPY_BYTES; i++) {
            assertEquals(expected.get(i), actual.get(i), what + ": byte " + i);
        }
    }

    private static ByteBuffer solidTexture(int width, int height) {
        final ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int i = 0; i < width * height; i++) {
            pixels.put((byte) R);
            pixels.put((byte) G);
            pixels.put((byte) B);
            pixels.put((byte) A);
        }
        return pixels.flip();
    }

    private static void assertPixels(ByteBuffer pixels, int count) {
        for (int i = 0; i < count; i++) {
            assertEquals(R, pixels.get(i * 4) & 0xFF, "red at pixel " + i);
            assertEquals(G, pixels.get(i * 4 + 1) & 0xFF, "green at pixel " + i);
            assertEquals(B, pixels.get(i * 4 + 2) & 0xFF, "blue at pixel " + i);
            assertEquals(A, pixels.get(i * 4 + 3) & 0xFF, "alpha at pixel " + i);
        }
    }
}
