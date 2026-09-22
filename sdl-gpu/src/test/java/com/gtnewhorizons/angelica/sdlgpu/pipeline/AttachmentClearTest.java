package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.FBOClearTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_D32_FLOAT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET;

class AttachmentClearTest {

    private static SdlTestRig rig;
    private static ShaderManager sm;
    private static PipelineStore store;
    private static AttachmentClear attachmentClear;

    private static final int SIZE = 32;

    private static long colorTexture;
    private static int colorFormat;
    private static long depthTexture;
    private static int depthFormat;

    @BeforeAll
    static void setUp() throws Exception {
        rig = SdlTestRig.acquireRealDevice();
        rig.resourceManager.cachePreferredDepthFormats();
        sm = new ShaderManager(rig.device);
        VAOManager.init(0);
        store = new PipelineStore(rig.device);
        attachmentClear = new AttachmentClear(rig.frameManager, store, sm);

        colorTexture = rig.resourceManager.createTexture(9700, GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, SIZE, SIZE, 1, 1);
        assertTrue(colorTexture != 0, "color target creation failed");
        colorFormat = rig.resourceManager.getTextureMeta(9700).sdlFormat();
        PipelineCache.setSwapchainFormats(new int[]{ colorFormat });

        rig.resourceManager.createTexture(9701, GL11.GL_TEXTURE_2D, GL30.GL_DEPTH24_STENCIL8, SIZE, SIZE, 1, 1);
        depthTexture = rig.resourceManager.ensureTextureUsage(9701, SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET);
        assertTrue(depthTexture != 0, "depth+stencil target creation failed");
        depthFormat = rig.resourceManager.getTextureMeta(9701).sdlFormat();
        assertTrue(PixelOps.isDepthStencilFormat(depthFormat), "test needs a packed depth+stencil format");
    }

    @AfterAll
    static void tearDown() {
        SdlTestRig.releaseRealDevice();
    }

    private static FboState makeFbo() {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = colorTexture;
        fbo.colorFormats[0] = colorFormat;
        fbo.colorAttachmentCount = 1;
        fbo.drawBuffers = new int[]{ 0 };
        fbo.cachedColorFormats = new int[]{ colorFormat };
        fbo.depthTexture = depthTexture;
        fbo.depthFormat = depthFormat;
        fbo.width = SIZE;
        fbo.height = SIZE;
        fbo.recomputeTargets();
        return fbo;
    }

    @Test
    void eligibleTruthTable() {
        final FboState fbo = makeFbo();
        final ContextState baseline = new ContextState();
        baseline.boundFboId = 1;
        final FrameManager.FrameState f = new FrameManager.FrameState();
        f.renderPass = 0xBEEFL;
        f.currentDepthTarget = fbo.depthTexture;
        f.currentColorTarget = fbo.primaryTarget;
        f.activeLayoutHash = fbo.structuralLayoutHash;
        assertTrue(attachmentClear.eligible(baseline, f, fbo, true, false), "baseline row must be eligible");

        final ContextState noFbo = new ContextState();
        noFbo.boundFboId = 0;
        assertFalse(attachmentClear.eligible(noFbo, f, fbo, true, false), "boundFboId==0 must be ineligible");

        final FrameManager.FrameState noPass = new FrameManager.FrameState();
        noPass.currentDepthTarget = fbo.depthTexture;
        noPass.currentColorTarget = fbo.primaryTarget;
        noPass.activeLayoutHash = fbo.structuralLayoutHash;
        assertFalse(attachmentClear.eligible(baseline, noPass, fbo, true, false), "renderPass==0 must be ineligible");

        final FrameManager.FrameState wrongDepth = new FrameManager.FrameState();
        wrongDepth.renderPass = 0xBEEFL;
        wrongDepth.currentDepthTarget = fbo.depthTexture + 1;
        wrongDepth.currentColorTarget = fbo.primaryTarget;
        wrongDepth.activeLayoutHash = fbo.structuralLayoutHash;
        assertFalse(attachmentClear.eligible(baseline, wrongDepth, fbo, true, false), "depth target mismatch must be ineligible");

        final FrameManager.FrameState wrongLayout = new FrameManager.FrameState();
        wrongLayout.renderPass = 0xBEEFL;
        wrongLayout.currentDepthTarget = fbo.depthTexture;
        wrongLayout.currentColorTarget = fbo.primaryTarget;
        wrongLayout.activeLayoutHash = fbo.structuralLayoutHash ^ 1L;
        assertFalse(attachmentClear.eligible(baseline, wrongLayout, fbo, true, false), "layout hash mismatch must be ineligible");

        final FrameManager.FrameState wrongColor = new FrameManager.FrameState();
        wrongColor.renderPass = 0xBEEFL;
        wrongColor.currentDepthTarget = fbo.depthTexture;
        wrongColor.currentColorTarget = fbo.primaryTarget + 1;
        wrongColor.activeLayoutHash = fbo.structuralLayoutHash;
        assertFalse(attachmentClear.eligible(baseline, wrongColor, fbo, true, false), "color target mismatch must be ineligible");

        final ContextState pending = new ContextState();
        pending.boundFboId = 1;
        FBOClearTracker.recordPendingDepthClear(pending, fbo.depthTexture, 1.0f);
        assertFalse(attachmentClear.eligible(pending, f, fbo, true, false), "a pending deferred clear must be ineligible");

        final FboState depthOnlyFormat = makeFbo();
        depthOnlyFormat.depthFormat = SDL_GPU_TEXTUREFORMAT_D32_FLOAT;
        assertFalse(attachmentClear.eligible(baseline, f, depthOnlyFormat, false, true), "stencil clear on a non depth-stencil format must be ineligible");
        assertTrue(attachmentClear.eligible(baseline, f, depthOnlyFormat, true, false), "depth clear on a depth-only format stays eligible");

        final FboState zeroSize = makeFbo();
        zeroSize.width = 0;
        assertFalse(attachmentClear.eligible(baseline, f, zeroSize, true, false), "zero-size target must be ineligible");

        final ContextState scissoredEmpty = new ContextState();
        scissoredEmpty.boundFboId = 1;
        scissoredEmpty.scissorEnabled = true;
        scissoredEmpty.scissorX = SIZE + 10;
        scissoredEmpty.scissorY = 0;
        scissoredEmpty.scissorW = 4;
        scissoredEmpty.scissorH = 4;
        assertFalse(attachmentClear.eligible(scissoredEmpty, f, fbo, true, false), "a scissor clamped to empty must be ineligible");
    }

    @Test
    void onePipelinePerLayoutModeAndMask() {
        final FboState fbo = makeFbo();
        final long p1 = attachmentClear.getOrCreatePipeline(fbo, true, false, 0);
        final long p1Again = attachmentClear.getOrCreatePipeline(fbo, true, false, 0);
        assertTrue(p1 != 0, "pipeline build must succeed");
        assertEquals(p1, p1Again, "the same (layout, mode, mask) must reuse the cached pipeline");

        final long p2 = attachmentClear.getOrCreatePipeline(fbo, false, true, 0xFF);
        assertNotEquals(p1, p2, "a different clear mode must build a different pipeline");

        final long p3 = attachmentClear.getOrCreatePipeline(fbo, false, true, 0x0F);
        assertNotEquals(p2, p3, "a different stencil write mask must build a different pipeline");

        final FboState depthOnlyLayout = makeFbo();
        depthOnlyLayout.drawBuffers = new int[0];
        depthOnlyLayout.cachedColorFormats = new int[0];
        final long p4 = attachmentClear.getOrCreatePipeline(depthOnlyLayout, true, false, 0);
        assertTrue(p4 != 0, "a depth-only layout pipeline must still build");
        assertNotEquals(p1, p4, "a different color-target layout must build a different pipeline");
    }
}
