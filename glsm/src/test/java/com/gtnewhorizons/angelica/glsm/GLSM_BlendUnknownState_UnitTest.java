package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.gtnewhorizons.angelica.util.GLSMUtil.resetGLState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link com.gtnewhorizons.angelica.glsm.states.BooleanState#setUnknownState()}.
 * Verifies that per-buffer blend operations (glEnablei/glDisablei) properly invalidate
 * the GLSM blend cache so subsequent global blend enable/disable calls are not skipped.
 */
@GLCompatTest
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

    @ParameterizedTest(name = "global blend enabled={0}")
    @ValueSource(booleans = {true, false})
    void perBufferBlendToggleInvalidatesCache(boolean enabled) {
        setBlend(enabled);
        assertEquals(enabled, GL11.glIsEnabled(GL11.GL_BLEND));

        // Flips buffer 0 behind the cache and marks it unknown, so the repeated global call must reach the driver
        if (enabled) {
            RenderSystem.disableBufferBlend(0);
        } else {
            RenderSystem.enableBufferBlend(0);
        }

        setBlend(enabled);
        assertEquals(enabled, GL11.glIsEnabled(GL11.GL_BLEND), "global blend state must be re-issued after a per-buffer toggle");
    }

    private static void setBlend(boolean enabled) {
        if (enabled) {
            GLStateManager.enableBlend();
        } else {
            GLStateManager.disableBlend();
        }
    }
}
