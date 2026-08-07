package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUBufferCreateInfo;
import org.lwjgl.sdl.SDL_GPUBufferRegion;
import org.lwjgl.sdl.SDL_GPUTransferBufferCreateInfo;
import org.lwjgl.sdl.SDL_GPUTransferBufferLocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.stackPush;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UploadArenaGpuRoundTripTest {

    private static Device device;
    private static FrameManager frameManager;
    private static ResourceManager resourceManager;
    private static long sdlDevice;
    private static int nextGlId = 1;

    @BeforeAll
    static void setUp() throws Exception {
        final SdlTestRig rig = SdlTestRig.acquireRealDevice();
        device = rig.device;
        frameManager = rig.frameManager;
        resourceManager = rig.resourceManager;
        sdlDevice = rig.sdlHandle;
    }

    @AfterAll
    static void tearDown() {
        if (frameManager != null && frameManager.isFrameActive()) {
            frameManager.endFrame();
        }
        SdlTestRig.releaseRealDevice();
    }

    private static void beginFrame() {
        frameManager.beginFrame();
        frameManager.frame().swapchainUnavailable = true; // headless: never touch the swapchain
    }

    private static void endFrame() {
        resourceManager.flushUploadArena();
        frameManager.endFrame();
    }

    private static ByteBuffer pattern(int size, int seed) {
        final ByteBuffer buf = MemoryUtil.memAlloc(size);
        for (int i = 0; i < size; i++) {
            buf.put(i, (byte) (seed * 31 + i));
        }
        return buf;
    }

    private static void assertPattern(ByteBuffer actual, int size, int seed, String what) {
        int nonzero = 0;
        for (int i = 0; i < size; i++) {
            if (actual.get(i) != 0) nonzero++;
        }
        assertTrue(nonzero > 0, what + ": buffer is all zeros (" + size + " bytes) - upload never landed");
        for (int i = 0; i < size; i++) {
            assertEquals((byte) (seed * 31 + i), actual.get(i), what + ": mismatch at byte " + i);
        }
    }

    private static ByteBuffer download(long gpuBuffer, int size) {
        try (var stack = stackPush()) {
            final SDL_GPUTransferBufferCreateInfo ci = SDL_GPUTransferBufferCreateInfo.calloc(stack)
                .usage(SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD)
                .size(size);
            final long xfer = SDL_CreateGPUTransferBuffer(sdlDevice, ci);
            assertTrue(xfer != 0, "download transfer buffer: " + SDLError.SDL_GetError());

            final long cb = SDL_AcquireGPUCommandBuffer(sdlDevice);
            final long cp = SDL_BeginGPUCopyPass(cb);
            final SDL_GPUBufferRegion src = SDL_GPUBufferRegion.calloc(stack).buffer(gpuBuffer).offset(0).size(size);
            final SDL_GPUTransferBufferLocation dst = SDL_GPUTransferBufferLocation.calloc(stack).transfer_buffer(xfer).offset(0);
            SDL_DownloadFromGPUBuffer(cp, src, dst);
            SDL_EndGPUCopyPass(cp);
            final long fence = SDL_SubmitGPUCommandBufferAndAcquireFence(cb);
            assertTrue(fence != 0, "submit+fence: " + SDLError.SDL_GetError());
            final PointerBuffer fences = stack.pointers(fence);
            SDL_WaitForGPUFences(sdlDevice, true, fences);
            SDL_ReleaseGPUFence(sdlDevice, fence);

            final ByteBuffer mapped = SDL_MapGPUTransferBuffer(sdlDevice, xfer, false, size);
            final ByteBuffer copy = MemoryUtil.memAlloc(size);
            MemoryUtil.memCopy(MemoryUtil.memAddress(mapped), MemoryUtil.memAddress(copy), size);
            SDL_UnmapGPUTransferBuffer(sdlDevice, xfer);
            SDL_ReleaseGPUTransferBuffer(sdlDevice, xfer);
            return copy;
        }
    }

    private static long createVertexBuffer(int size) {
        return resourceManager.createBuffer(nextGlId++, SDL_GPU_BUFFERUSAGE_VERTEX, size);
    }

    private static long createRawBuffer(int size) {
        try (var stack = stackPush()) {
            final SDL_GPUBufferCreateInfo ci = SDL_GPUBufferCreateInfo.calloc(stack)
                .usage(SDL_GPU_BUFFERUSAGE_VERTEX)
                .size(size);
            return SDL_CreateGPUBuffer(sdlDevice, ci);
        }
    }

    @Test
    @Order(1)
    void singleFrameRoundTrip() {
        final int size = 54864; // DE armor OBJ list size
        beginFrame();
        final long dst = createVertexBuffer(size);
        assertTrue(dst != 0);
        final ByteBuffer data = pattern(size, 7);
        assertTrue(resourceManager.arenaUpload(data, dst, 0, false));
        MemoryUtil.memFree(data);
        endFrame();

        SDL_WaitForGPUIdle(sdlDevice);
        final ByteBuffer got = download(dst, size);
        assertPattern(got, size, 7, "single-frame upload");
        MemoryUtil.memFree(got);
    }

    @Test
    @Order(2)
    void uploadSurvivesManyFramesOfArenaChurnAndFragmentation() {
        final int size = 117072;
        final int junkSize = 64 * 1024;

        beginFrame();
        final long armor = createVertexBuffer(size);
        assertTrue(armor != 0);
        final ByteBuffer armorData = pattern(size, 3);
        assertTrue(resourceManager.arenaUpload(armorData, armor, 0, false));
        MemoryUtil.memFree(armorData);
        final long junkDst = createVertexBuffer(junkSize);
        endFrame();

        final ByteBuffer junk = pattern(junkSize, 9);
        final long[] churn = new long[8];
        for (int frame = 0; frame < 60; frame++) {
            beginFrame();
            for (int i = 0; i < churn.length; i++) {
                if (churn[i] != 0) SDL_ReleaseGPUBuffer(sdlDevice, churn[i]);
                churn[i] = createRawBuffer(3 * 1024 * 1024); // >2MiB: shared large-page allocations
            }
            for (int i = 0; i < 6; i++) {
                junk.position(0).limit(junkSize);
                assertTrue(resourceManager.arenaUpload(junk, junkDst, 0, false));
            }
            endFrame();
        }
        MemoryUtil.memFree(junk);
        for (long handle : churn) {
            if (handle != 0) SDL_ReleaseGPUBuffer(sdlDevice, handle);
        }

        SDL_WaitForGPUIdle(sdlDevice);
        final ByteBuffer got = download(armor, size);
        assertPattern(got, size, 3, "upload after 60 frames of churn");
        MemoryUtil.memFree(got);
    }

    @Test
    @Order(4)
    void replayOfDeArmorLoadTrace() {
        final int bigSize = 14854464;
        final int medSize = 3713616;
        final int[] armorSizes = { 78624, 78624, 54864, 54864, 54864, 54864, 117072, 117072 };

        // frame 66
        beginFrame();
        long big = createVertexBuffer(bigSize);
        ByteBuffer bigData = pattern(bigSize, 41);
        assertTrue(resourceManager.arenaUpload(bigData, big, 0, false));
        MemoryUtil.memFree(bigData);
        final long med = createVertexBuffer(medSize);
        final ByteBuffer medData = pattern(medSize, 42);
        assertTrue(resourceManager.arenaUpload(medData, med, 0, false));
        MemoryUtil.memFree(medData);
        endFrame();

        final int bigGlId = nextGlId - 2;
        beginFrame();
        resourceManager.deleteBuffer(bigGlId);
        big = resourceManager.createBuffer(bigGlId, SDL_GPU_BUFFERUSAGE_VERTEX, bigSize);
        bigData = pattern(bigSize, 43);
        assertTrue(resourceManager.arenaUpload(bigData, big, 0, false));
        MemoryUtil.memFree(bigData);
        endFrame();

        final long[] armor = new long[armorSizes.length];
        beginFrame();
        for (int i = 0; i < armorSizes.length; i++) {
            armor[i] = createVertexBuffer(armorSizes[i]);
            final ByteBuffer data = pattern(armorSizes[i], 50 + i);
            assertTrue(resourceManager.arenaUpload(data, armor[i], 0, false));
            MemoryUtil.memFree(data);
        }
        endFrame();

        for (int frame = 0; frame < 30; frame++) {
            beginFrame();
            resourceManager.deleteBuffer(bigGlId);
            big = resourceManager.createBuffer(bigGlId, SDL_GPU_BUFFERUSAGE_VERTEX, bigSize);
            bigData = pattern(bigSize, 60 + frame);
            assertTrue(resourceManager.arenaUpload(bigData, big, 0, false));
            MemoryUtil.memFree(bigData);
            endFrame();
        }

        SDL_WaitForGPUIdle(sdlDevice);
        for (int i = 0; i < armorSizes.length; i++) {
            final ByteBuffer got = download(armor[i], armorSizes[i]);
            assertPattern(got, armorSizes[i], 50 + i, "armor buffer " + i + " (size " + armorSizes[i] + ")");
            MemoryUtil.memFree(got);
        }
    }

    @Test
    @Order(5)
    void midFrameReadbackFlushSeesPriorUploadsAndKeepsLaterOnes() {
        final int size = 54864;
        beginFrame();
        final long a = createVertexBuffer(size);
        final ByteBuffer dataA = pattern(size, 11);
        assertTrue(resourceManager.arenaUpload(dataA, a, 0, false));
        MemoryUtil.memFree(dataA);

        resourceManager.flushUploadArena();
        final ByteBuffer readback = download(a, size);
        assertPattern(readback, size, 11, "mid-frame readback of A");

        final long b = createVertexBuffer(size);
        assertTrue(resourceManager.arenaUpload(readback, b, 0, false));
        MemoryUtil.memFree(readback);
        endFrame();

        SDL_WaitForGPUIdle(sdlDevice);
        final ByteBuffer got = download(b, size);
        assertPattern(got, size, 11, "B built from mid-frame readback");
        MemoryUtil.memFree(got);
    }

    @Test
    @Order(3)
    void midFrameOverflowGrowKeepsAllUploads() {
        final int chunk = 5 * 1024 * 1024;
        final int chunks = 5;
        final long[] dsts = new long[chunks];
        beginFrame();
        for (int i = 0; i < chunks; i++) {
            dsts[i] = createVertexBuffer(chunk);
            final ByteBuffer data = pattern(chunk, 20 + i);
            assertTrue(resourceManager.arenaUpload(data, dsts[i], 0, false));
            MemoryUtil.memFree(data);
        }
        endFrame();

        SDL_WaitForGPUIdle(sdlDevice);
        for (int i = 0; i < chunks; i++) {
            final ByteBuffer got = download(dsts[i], chunk);
            assertPattern(got, chunk, 20 + i, "grow-path upload " + i);
            MemoryUtil.memFree(got);
        }
    }
}
