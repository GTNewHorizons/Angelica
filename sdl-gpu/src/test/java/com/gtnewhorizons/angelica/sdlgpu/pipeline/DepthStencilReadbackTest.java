package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.FBOClearTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.resource.TextureOps;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.sdl.SDL_GPUDepthStencilTargetInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_LOADOP_LOAD;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_STOREOP_STORE;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET;
import static org.lwjgl.system.MemoryStack.stackPush;

@Tag("glsm-sdl")
@Timeout(120)
class DepthStencilReadbackTest {

    private static final int W = 24;
    private static final int H = 16;
    private static final int RX = 4;
    private static final int RY = 2;
    private static final int RW = 8;
    private static final int RH = 5;

    private static SdlTestRig rig;
    private static FrameManager frameManager;
    private static ShaderManager sm;
    private static PipelineStore store;
    private static FBOClearTracker clearTracker;
    private static TextureOps textureOps;
    private static AttachmentClear attachmentClear;
    private static DepthStencilReadback readback;
    private static ContextState st;
    private static long depthTexture;
    private static int depthFormat;

    @BeforeAll
    static void setUp() throws Exception {
        rig = SdlTestRig.acquireRealDevice();
        frameManager = rig.frameManager;
        rig.resourceManager.cachePreferredDepthFormats();
        sm = new ShaderManager(rig.device);
        store = new PipelineStore(rig.device);
        clearTracker = new FBOClearTracker(frameManager, rig.resourceManager, sm);
        textureOps = new TextureOps(rig.device, frameManager, rig.resourceManager, clearTracker);
        attachmentClear = new AttachmentClear(frameManager, store, sm);
        readback = new DepthStencilReadback(frameManager, rig.resourceManager, store, sm, clearTracker);
        st = new ContextState();

        rig.resourceManager.createTexture(9801, GL11.GL_TEXTURE_2D, GL30.GL_DEPTH24_STENCIL8, W, H, 1, 1);
        depthTexture = rig.resourceManager.ensureTextureUsage(9801, SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET);
        assertNotEquals(0L, depthTexture, "depth+stencil texture creation failed");
        depthFormat = rig.resourceManager.getTextureMeta(9801).sdlFormat();
        assertTrue(PixelOps.isDepthStencilFormat(depthFormat), "test needs a packed depth+stencil format");
    }

    @AfterAll
    static void tearDown() {
        if (frameManager != null && frameManager.isFrameActive()) frameManager.endFrame();
        if (readback != null) {
            readback.release(st);
            readback.shutdown();
        }
        if (store != null) store.shutdown();
        SdlTestRig.releaseRealDevice();
    }

    private static void clearRegion(float depth, int stencil) {
        final FboState fbo = new FboState();
        fbo.drawBuffers = new int[0];
        fbo.cachedColorFormats = new int[0];
        fbo.depthTexture = depthTexture;
        fbo.depthFormat = depthFormat;
        fbo.width = W;
        fbo.height = H;
        st.scissorEnabled = true;
        st.scissorX = RX;
        st.scissorY = RY;
        st.scissorW = RW;
        st.scissorH = RH;
        st.depthClearValue = depth;
        st.stencilClearValue = stencil;
        st.pipeline.stencilWriteMask = 0xFF;
        try (MemoryStack stack = stackPush()) {
            final SDL_GPUDepthStencilTargetInfo ds = SDL_GPUDepthStencilTargetInfo.calloc(stack);
            final long addr = ds.address();
            MemoryAccess.putAddress(addr + SDL_GPUDepthStencilTargetInfo.TEXTURE, depthTexture);
            MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.LOAD_OP, SDL_GPU_LOADOP_LOAD);
            MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
            MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STENCIL_LOAD_OP, SDL_GPU_LOADOP_LOAD);
            MemoryAccess.putInt(addr + SDL_GPUDepthStencilTargetInfo.STENCIL_STORE_OP, SDL_GPU_STOREOP_STORE);
            frameManager.beginRenderPass(null, ds);
            assertTrue(attachmentClear.clearInPass(st, frameManager.frame(), attachmentClear.forFbo(fbo), false, true, true), "scissored clear failed");
            frameManager.endRenderPassIfActive();
        }
        st.scissorEnabled = false;
    }

    private static ByteBuffer download(long scratch, int bytesPerTexel) {
        frameManager.submitMidFrame();
        final ByteBuffer out = MemoryUtil.memAlloc(W * H * bytesPerTexel);
        textureOps.readbackTexture(scratch, 0, 0, W, H, 0, out);
        out.rewind();
        return out;
    }

    private static boolean inRegion(int x, int y) {
        return x >= RX && x < RX + RW && y >= RY && y < RY + RH;
    }

    @Test
    void readsPendingClearThenScissoredRegion() {
        frameManager.beginFrame();
        FBOClearTracker.recordPendingDepthClear(st, depthTexture, 0.25f);
        FBOClearTracker.recordPendingStencilClear(st, depthTexture, 0xA5);

        final long depthScratch = readback.renderDepth(st, depthTexture, depthFormat, W, H);
        assertNotEquals(0L, depthScratch, "depth readback pass failed");
        final ByteBuffer d0 = download(depthScratch, 4);
        try {
            for (int i = 0; i < W * H; i++) assertEquals(0.25f, d0.getFloat(i * 4), 1e-6f, "pending depth clear at texel " + i);
        } finally {
            MemoryUtil.memFree(d0);
        }

        clearRegion(0.75f, 0x3C);

        final long stencilScratch = readback.renderStencil(st, depthTexture, depthFormat, W, H);
        assertNotEquals(0L, stencilScratch, "stencil readback pass failed");
        final ByteBuffer s = download(stencilScratch, 1);
        try {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    assertEquals(inRegion(x, y) ? 0x3C : 0xA5, s.get(y * W + x) & 0xFF, "stencil at x=" + x + " y=" + y);
                }
            }
        } finally {
            MemoryUtil.memFree(s);
        }

        assertEquals(depthScratch, readback.renderDepth(st, depthTexture, depthFormat, W, H), "same-size readback must reuse the scratch");
        final ByteBuffer d1 = download(depthScratch, 4);
        try {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    assertEquals(inRegion(x, y) ? 0.75f : 0.25f, d1.getFloat((y * W + x) * 4), 1e-6f, "depth at x=" + x + " y=" + y);
                }
            }
        } finally {
            MemoryUtil.memFree(d1);
        }

        final long stencilAgain = readback.renderStencil(st, depthTexture, depthFormat, W, H);
        final ByteBuffer s2 = download(stencilAgain, 1);
        try {
            assertEquals(0x3C, s2.get(RY * W + RX) & 0xFF, "stencil must survive the readback passes");
            assertEquals(0xA5, s2.get(0) & 0xFF, "stencil must survive the readback passes");
        } finally {
            MemoryUtil.memFree(s2);
        }
        frameManager.endFrame();
    }
}
