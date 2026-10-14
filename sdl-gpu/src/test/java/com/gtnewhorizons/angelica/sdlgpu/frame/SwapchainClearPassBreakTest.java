package com.gtnewhorizons.angelica.sdlgpu.frame;

import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.fbo0PassStaysOpen;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwapchainClearPassBreakTest {

    private static FrameManager.FrameState activeFbo0Pass() {
        final FrameManager.FrameState f = new FrameManager.FrameState();
        f.renderPass = 0xBEEFL;
        f.activeLayoutHash = FrameManager.FBO0_LAYOUT_HASH;
        return f;
    }

    @Test
    void aPendingColorClearBreaksTheActivePass() {
        final ContextState st = new ContextState();
        st.pendingSwapchainClear = true;
        assertFalse(fbo0PassStaysOpen(st, activeFbo0Pass()), "otherwise the clear is deferred to the next pass and wipes what this one drew");
    }

    @Test
    void aPendingDepthOrStencilClearBreaksTheActivePass() {
        final ContextState depth = new ContextState();
        depth.pendingSwapchainDepthClear = true;
        assertFalse(fbo0PassStaysOpen(depth, activeFbo0Pass()));

        final ContextState stencil = new ContextState();
        stencil.pendingSwapchainStencilClear = true;
        assertFalse(fbo0PassStaysOpen(stencil, activeFbo0Pass()));
    }

    @Test
    void nothingPendingLeavesThePassAlone() {
        assertTrue(fbo0PassStaysOpen(new ContextState(), activeFbo0Pass()));
    }

    @Test
    void aClearWithNoActivePassNeedsNoBreak() {
        final ContextState st = new ContextState();
        st.pendingSwapchainClear = true;

        final FrameManager.FrameState f = activeFbo0Pass();
        f.renderPass = 0;
        assertFalse(fbo0PassStaysOpen(st, f));
    }

    @Test
    void aPassBoundToAnFboIsNotTheFbo0Pass() {
        final ContextState st = new ContextState();
        st.pendingSwapchainClear = true;

        final FrameManager.FrameState f = activeFbo0Pass();
        f.activeLayoutHash = 0xF80L;
        assertFalse(fbo0PassStaysOpen(st, f));
    }
}
