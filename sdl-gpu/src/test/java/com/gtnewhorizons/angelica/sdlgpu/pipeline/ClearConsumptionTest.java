package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FBOClearTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier.consumeClears;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_D24_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;

class ClearConsumptionTest {

    private static final long COLOR = 0xC010L;
    private static final long DEPTH = 0xD00DL;
    private static final long DUMMY = 0xD04DL;

    private static FboState fbo(int depthFormat) {
        final FboState fbo = new FboState();
        fbo.colorTextures[0] = COLOR;
        fbo.colorGlIds[0] = 3;
        if (depthFormat != 0) {
            fbo.depthTexture = DEPTH;
            fbo.depthGlId = 5;
            fbo.depthFormat = depthFormat;
        }
        fbo.recomputeTargets();
        return fbo;
    }

    private static ResourceManager rm() {
        return SdlTestRig.resourceManager();
    }

    @Test
    void aClearedColorTargetBecomesContentDefined() {
        final ResourceManager rm = rm();
        final ContextState st = new ContextState();
        final FboState fbo = fbo(0);
        FBOClearTracker.recordPendingColorClear(st, COLOR, 0f, 0f, 0f, 1f);

        consumeClears(rm, st, fbo, DUMMY, 0b1, false, false, false);

        assertFalse(st.pendingColorTextures.contains(COLOR), "the pass applied the clear, so it is no longer pending");
        assertTrue(rm.isTextureContentDefined(COLOR), "a later frame must not clear it again");
    }

    @Test
    void anUnclearedColorTargetIsNotMarkedDefined() {
        final ResourceManager rm = rm();
        final ContextState st = new ContextState();

        consumeClears(rm, st, fbo(0), DUMMY, 0, false, false, false);

        assertFalse(rm.isTextureContentDefined(COLOR));
    }

    @Test
    void theDummyTargetIsNeverTracked() {
        final ResourceManager rm = rm();
        final ContextState st = new ContextState();
        final FboState fbo = fbo(0);
        fbo.colorTextures[0] = DUMMY;
        fbo.recomputeTargets();

        consumeClears(rm, st, fbo, DUMMY, 0b1, false, false, false);

        assertFalse(rm.isTextureContentDefined(DUMMY), "the shared stand-in target has no per-FBO content");
    }

    @Test
    void depthAndStencilAreConsumedIndependently() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingDepthClear(st, DEPTH, 1f);
        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0);

        consumeClears(rm(), st, fbo(SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT), DUMMY, 0, true, true, true);

        assertFalse(st.pendingDepthTextures.contains(DEPTH));
        assertFalse(st.pendingStencilTextures.contains(DEPTH));
        assertTrue(st.clearedTexturesThisFrame.contains(DEPTH));
        assertTrue(st.clearedStencilTexturesThisFrame.contains(DEPTH));
    }

    @Test
    void aDepthOnlyTargetLeavesStencilStateAlone() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingDepthClear(st, DEPTH, 1f);
        FBOClearTracker.recordPendingStencilClear(st, DEPTH, 0);

        consumeClears(rm(), st, fbo(SDL_GPU_TEXTUREFORMAT_D24_UNORM), DUMMY, 0, true, false, false);

        assertFalse(st.pendingDepthTextures.contains(DEPTH));
        assertTrue(st.pendingStencilTextures.contains(DEPTH), "the format has no stencil aspect to clear");
        assertFalse(st.clearedStencilTexturesThisFrame.contains(DEPTH));
    }

    @Test
    void anUnclearedDepthTargetIsStillConsumed() {
        final ContextState st = new ContextState();
        FBOClearTracker.recordPendingDepthClear(st, DEPTH, 1f);

        consumeClears(rm(), st, fbo(SDL_GPU_TEXTUREFORMAT_D24_UNORM), DUMMY, 0, false, false, false);

        assertFalse(st.pendingDepthTextures.contains(DEPTH));
        assertFalse(st.clearedTexturesThisFrame.contains(DEPTH), "no clear was baked, so nothing was cleared");
    }
}
