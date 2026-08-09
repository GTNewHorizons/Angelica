package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;

class StencilClearTrackingTest {

    private static final long DEPTH = 0xD00DL;

    private static FBOClearTracker tracker() {
        final SdlTestRig rig = SdlTestRig.create();
        return new FBOClearTracker(rig.frameManager, rig.resourceManager, null);
    }

    private static FboState packedDepthStencilFbo() {
        final FboState fbo = new FboState();
        fbo.depthTexture = DEPTH;
        fbo.depthGlId = 5;
        fbo.depthFormat = SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;
        fbo.recomputeTargets();
        return fbo;
    }

    @Test
    void pendingStencilClearIsTrackedSeparatelyFromDepth() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0);

        assertTrue(st.pendingStencilTextures.contains(DEPTH));
        assertFalse(st.pendingDepthTextures.contains(DEPTH), "a stencil clear must not imply a depth clear");
    }

    @Test
    void stencilClearSurvivesADepthClearOnTheSameHandle() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 3);
        FBOClearTracker.recordPendingDepthClear(st, DEPTH, 1f);

        assertTrue(st.pendingStencilTextures.contains(DEPTH), "depth and stencil share a texture but not a clear");
        assertEquals(3, st.pendingStencilValues.get(DEPTH));
    }

    @Test
    void aPendingStencilClearAloneBlocksTargetReuse() {
        final ContextState st = new ContextState();
        final FboState fbo = packedDepthStencilFbo();
        assertFalse(FBOClearTracker.fboHasPendingClear(st, fbo));

        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0);
        assertTrue(FBOClearTracker.fboHasPendingClear(st, fbo), "the pass must be rebuilt to apply the stencil clear");
    }

    @Test
    void recordingAStencilClearRetractsTheClearedThisFrameMark() {
        final ContextState st = new ContextState();
        st.clearedStencilTexturesThisFrame.add(DEPTH);

        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0);
        assertFalse(st.clearedStencilTexturesThisFrame.contains(DEPTH), "a second clear in one frame must not be swallowed");
    }

    @Test
    void recordingBumpsMutationGen() {
        final ContextState st = new ContextState();
        FBOClearTracker.snapshotFlushGenerations(st);
        final int before = st.pendingMutationGen;

        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0);
        assertEquals(before + 1, st.pendingMutationGen);
    }

    @Test
    void scrubDropsStencilStateWithTheTexture() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 7);
        st.clearedStencilTexturesThisFrame.add(DEPTH);

        final SdlTestRig rig = SdlTestRig.create();
        final int glId = rig.resourceManager.genTexture();
        SdlReflect.putTextureHandle(rig.resourceManager, glId, DEPTH);
        new FBOClearTracker(rig.frameManager, rig.resourceManager, null).scrubPendingClearsForTexture(st, glId);

        assertFalse(st.pendingStencilTextures.contains(DEPTH));
        assertFalse(st.pendingStencilValues.containsKey(DEPTH));
        assertFalse(st.clearedStencilTexturesThisFrame.contains(DEPTH));
    }

    @Test
    void stencilClearValueIsMaskedToEightBits() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0xFF);
        assertEquals(0xFF, st.pendingStencilValues.get(DEPTH));
        assertEquals((byte) 0xFF, (byte) st.pendingStencilValues.get(DEPTH));
    }

    @Test
    void aStencilOnlyClearStillReachesTheSamplerFlush() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0);
        final int gen = st.pendingMutationGen;

        tracker().flushPendingClearsForBoundSamplers(st);

        assertEquals(gen, st.lastFlushedPendingMutationGen, "the early-out must not skip a texture whose only pending clear is stencil");
    }

    @Test
    void trackerIsUnusedForFbosWithoutADepthAttachment() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0);
        final FboState colorOnly = new FboState();
        colorOnly.colorTextures[0] = 0xAAAAL;
        colorOnly.recomputeTargets();
        assertFalse(FBOClearTracker.fboHasPendingClear(st, colorOnly));
    }
}
