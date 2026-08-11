package com.gtnewhorizons.angelica.sdlgpu.frame;

import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.swapchainClearNeedsPassBreak;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwapchainClearPassBreakTest {

    private static final long SWAPCHAIN = 0x5A1CL;

    private static FrameManager.FrameState activeSwapchainPass() {
        final FrameManager.FrameState f = new FrameManager.FrameState();
        f.swapchainTexture = SWAPCHAIN;
        f.renderPass = 0xBEEFL;
        f.currentColorTarget = SWAPCHAIN;
        return f;
    }

    @Test
    void aPendingColorClearBreaksTheActivePass() {
        final ContextState st = new ContextState();
        st.pendingSwapchainClear = true;
        assertTrue(swapchainClearNeedsPassBreak(st, activeSwapchainPass()), "otherwise the clear is deferred to the next pass and wipes what this one drew");
    }

    @Test
    void aPendingDepthOrStencilClearBreaksTheActivePass() {
        final ContextState depth = new ContextState();
        depth.pendingSwapchainDepthClear = true;
        assertTrue(swapchainClearNeedsPassBreak(depth, activeSwapchainPass()));

        final ContextState stencil = new ContextState();
        stencil.pendingSwapchainStencilClear = true;
        assertTrue(swapchainClearNeedsPassBreak(stencil, activeSwapchainPass()));
    }

    @Test
    void nothingPendingLeavesThePassAlone() {
        assertFalse(swapchainClearNeedsPassBreak(new ContextState(), activeSwapchainPass()));
    }

    @Test
    void aClearWithNoActivePassNeedsNoBreak() {
        final ContextState st = new ContextState();
        st.pendingSwapchainClear = true;

        final FrameManager.FrameState f = activeSwapchainPass();
        f.renderPass = 0;
        assertFalse(swapchainClearNeedsPassBreak(st, f));
    }

    @Test
    void aPassBoundToAnFboIsNotTheSwapchainPass() {
        final ContextState st = new ContextState();
        st.pendingSwapchainClear = true;

        final FrameManager.FrameState f = activeSwapchainPass();
        f.currentColorTarget = 0xF80L;
        assertFalse(swapchainClearNeedsPassBreak(st, f));
    }
}
