package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

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
    void copyWithNoPendingClearDefinesTheDestination() {
        final ContextState st = new ContextState();
        final SdlTestRig rig = SdlTestRig.create();
        final FBOClearTracker tracker = new FBOClearTracker(rig.frameManager, rig.resourceManager, null);
        tracker.resolveDestinationForWrite(st, HANDLE, meta(), 0, 0, 0, 0, W, H, true);

        assertTrue(rig.resourceManager.isTextureContentDefined(HANDLE), "a copy is a write, so the next pass must load it, not clear it");
    }

    @Test
    void depthStencilClearsDropOnlyForAnAllAspectOverwrite() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingDepthClear(st, HANDLE, 1f);
        FBOClearTracker.recordPendingStencilClear(st, HANDLE, 0);
        final FBOClearTracker tracker = tracker();

        assertFalse(tracker.discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta(), false));
        assertTrue(st.pendingDepthTextures.contains(HANDLE) && st.pendingStencilTextures.contains(HANDLE), "a single-aspect overwrite must materialize, not drop, the other aspect's clear");

        assertTrue(tracker.discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta(), true));
        assertFalse(st.pendingDepthTextures.contains(HANDLE) || st.pendingStencilTextures.contains(HANDLE));
        assertFalse(st.pendingDepthValues.containsKey(HANDLE) || st.pendingStencilValues.containsKey(HANDLE));
    }

    private static Stream<Arguments> clearsThatSurvive() {
        final ResourceManager.TextureMeta volume = new ResourceManager.TextureMeta(0, 0, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, W, H, 4, 1, 0);
        return Stream.of(
            Arguments.of("narrower", 0, 0, 0, W / 2, H, meta()),
            Arguments.of("shorter", 0, 0, 0, W, H / 2, meta()),
            Arguments.of("offset x", 1, 0, 0, W, H, meta()),
            Arguments.of("offset y", 0, 1, 0, W, H, meta()),
            Arguments.of("not level 0", 0, 0, 1, W, H, meta()),
            Arguments.of("volume slice", 0, 0, 0, W, H, volume),
            Arguments.of("missing meta", 0, 0, 0, W, H, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clearsThatSurvive")
    void partialCoverageKeepsTheClear(String name, int x, int y, int level, int w, int h, ResourceManager.TextureMeta meta) {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingColorClear(st, HANDLE, 1f, 0f, 0f, 1f);
        assertFalse(tracker().discardPendingClearIfFullyCovered(st, HANDLE, x, y, level, w, h, meta, true));
        assertTrue(st.pendingColorTextures.contains(HANDLE), "the clear must survive a partial overwrite");
    }

    @Test
    void noPendingClearIsNotAnOverwriteClaim() {
        final ContextState st = new ContextState();
        assertFalse(tracker().discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta(), true));
    }

    @Test
    void discardBumpsMutationGenSoSamplerFlushReevaluates() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingColorClear(st, HANDLE, 1f, 0f, 0f, 1f);
        FBOClearTracker.snapshotFlushGenerations(st);
        final int before = st.pendingMutationGen;

        assertTrue(tracker().discardPendingClearIfFullyCovered(st, HANDLE, 0, 0, 0, W, H, meta(), true));
        assertEquals(before + 1, st.pendingMutationGen);
        assertFalse(st.pendingColorTextures.contains(HANDLE), "pending clear must be dropped, not left queued");
        assertFalse(st.pendingColorValues.containsKey(HANDLE));
    }
}
