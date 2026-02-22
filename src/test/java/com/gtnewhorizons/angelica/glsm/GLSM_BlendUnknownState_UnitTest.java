package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.gtnewhorizons.angelica.util.GLSMUtil.resetGLState;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link com.gtnewhorizons.angelica.glsm.states.BooleanState#setUnknownState()}.
 * Verifies that per-buffer blend operations (glEnablei/glDisablei) properly invalidate
 * the GLSM blend cache so subsequent global blend enable/disable calls are not skipped.
 */
@ExtendWith(AngelicaExtension.class)
class GLSM_BlendUnknownState_UnitTest {

    @BeforeEach
    void setUp() {
        resetGLState();
    }

    @AfterEach
    void tearDown() {
        resetGLState();
    }

    @Test
    void testSetUnknownStateForcesCacheBypass() {
        // Enable blend via GLSM — both cache and GL should agree
        GLStateManager.enableBlend();
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "GL blend should be enabled");
        assertTrue(GLStateManager.getBlendMode().isEnabled(), "GLSM blend cache should be enabled");

        // Simulate a per-buffer disable that bypasses the cache (as glDisablei does)
        GL30.glDisablei(GL11.GL_BLEND, 0);
        GLStateManager.getBlendMode().setUnknownState();

        // Cache still says enabled, but GL state for buffer 0 is now disabled
        assertTrue(GLStateManager.getBlendMode().isEnabled(), "GLSM cache should still say enabled");

        // Re-enable blend via GLSM — without setUnknownState, this would be a no-op
        // because the cache thinks blend is already enabled
        GLStateManager.enableBlend();

        // Per GL spec, glEnable(GL_BLEND) enables blend for ALL draw buffers,
        // overriding any per-buffer disables
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "GL blend should be re-enabled after setUnknownState");
    }

    @Test
    void testDisableBufferBlendInvalidatesCache() {
        GLStateManager.enableBlend();
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));

        // Use RenderSystem which should call setUnknownState internally
        RenderSystem.disableBufferBlend(0);

        // Now re-enable via GLSM — should issue the GL call
        GLStateManager.enableBlend();
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND), "Blend should be re-enabled after disableBufferBlend");
    }

    @Test
    void testEnableBufferBlendInvalidatesCache() {
        // Start with blend disabled
        GLStateManager.disableBlend();
        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND));

        // Per-buffer enable bypasses cache
        RenderSystem.enableBufferBlend(0);

        // Disable via GLSM — should issue the GL call despite cache saying disabled
        GLStateManager.disableBlend();
        assertFalse(GL11.glIsEnabled(GL11.GL_BLEND), "Blend should be disabled after enableBufferBlend + disableBlend");
    }

    @Test
    void testUnknownStateClearedAfterSetEnabled() {
        GLStateManager.enableBlend();

        // Mark unknown
        GLStateManager.getBlendMode().setUnknownState();

        // First setEnabled clears the unknown flag
        GLStateManager.enableBlend();

        // Second setEnabled should use normal caching (no redundant GL call)
        // Verify state is consistent
        assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
        assertTrue(GLStateManager.getBlendMode().isEnabled());
    }
}
