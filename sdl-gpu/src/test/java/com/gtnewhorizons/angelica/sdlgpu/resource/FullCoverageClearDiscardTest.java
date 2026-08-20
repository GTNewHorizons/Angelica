package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;

class FullCoverageClearDiscardTest {

    private static final long HANDLE = 0xABCDL;
    private static final int W = 1024;
    private static final int H = 1024;

    private static FBOClearTracker tracker() {
        final SdlTestRig rig = SdlTestRig.create();
        return new FBOClearTracker(rig.frameManager, rig.resourceManager, null);
    }

    private static ResourceManager.TextureMeta meta() {
        return new ResourceManager.TextureMeta(0, 0, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, W, H, 1, 1, 0);
    }

    @Test
    void fullTargetColorBlitDropsThePendingClear() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingColorClear(st, HANDLE, 1f, 0f, 0f, 1f);

        assertTrue(tracker().discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta()));
        assertFalse(st.pendingColorTextures.contains(HANDLE), "pending clear must be dropped, not left queued");
        assertFalse(st.pendingColorValues.containsKey(HANDLE));
        assertTrue(st.clearedTexturesThisFrame.contains(HANDLE), "the overwrite counts as the clear");
    }

    @Test
    void fullTargetColorBlitDefinesTheContents() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingColorClear(st, HANDLE, 1f, 0f, 0f, 1f);

        final SdlTestRig rig = SdlTestRig.create();
        final FBOClearTracker tracker = new FBOClearTracker(rig.frameManager, rig.resourceManager, null);
        assertTrue(tracker.discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta()));

        assertTrue(rig.resourceManager.isTextureContentDefined(HANDLE), "the overwrite defines the contents, so a later frame must not clear it back");
    }

    @Test
    void fullTargetDepthBlitDropsThePendingClear() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingDepthClear(st, HANDLE, 1f);

        assertTrue(tracker().discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta()));
        assertFalse(st.pendingDepthTextures.contains(HANDLE));
        assertTrue(st.clearedTexturesThisFrame.contains(HANDLE));
    }

    @Test
    void partialCoverageKeepsTheClear() {
        final int[][] partial = {
            { 0, 0, 0, W / 2, H },      // narrower
            { 0, 0, 0, W, H / 2 },      // shorter
            { 1, 0, 0, W, H },          // offset x
            { 0, 1, 0, W, H },          // offset y
            { 0, 0, 1, W, H },          // not level 0
        };
        for (int[] c : partial) {
            final ContextState st = new ContextState();
            FBOClearTracker.recordPendingColorClear(st, HANDLE, 1f, 0f, 0f, 1f);
            assertFalse(tracker().discardPendingClearIfFullyCovered(st, HANDLE, c[0], c[1], c[2], c[3], c[4], meta()),
                "x=" + c[0] + " y=" + c[1] + " level=" + c[2] + " w=" + c[3] + " h=" + c[4]);
            assertTrue(st.pendingColorTextures.contains(HANDLE), "the clear must survive a partial overwrite");
        }
    }

    @Test
    void volumeTextureKeepsTheClear() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingColorClear(st, HANDLE, 1f, 0f, 0f, 1f);
        final ResourceManager.TextureMeta volume = new ResourceManager.TextureMeta(0, 0, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, W, H, 4, 1, 0);
        assertFalse(tracker().discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, volume),
            "one slice of a volume is not full coverage");
        assertTrue(st.pendingColorTextures.contains(HANDLE));
    }

    @Test
    void noPendingClearIsNotAnOverwriteClaim() {
        final ContextState st = new ContextState();
        assertFalse(tracker().discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta()));
        assertFalse(st.clearedTexturesThisFrame.contains(HANDLE));
    }

    @Test
    void missingMetaFallsBackToMaterializing() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingColorClear(st, HANDLE, 1f, 0f, 0f, 1f);
        assertFalse(tracker().discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, null));
        assertTrue(st.pendingColorTextures.contains(HANDLE));
    }

    @Test
    void discardBumpsMutationGenSoSamplerFlushReevaluates() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingColorClear(st, HANDLE, 1f, 0f, 0f, 1f);
        FBOClearTracker.snapshotFlushGenerations(st);
        final int before = st.pendingMutationGen;

        assertTrue(tracker().discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta()));
        assertEquals(before + 1, st.pendingMutationGen);
    }
}
