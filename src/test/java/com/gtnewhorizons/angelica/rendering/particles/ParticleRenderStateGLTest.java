package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class ParticleRenderStateGLTest {

    @AfterEach
    void restoreOverrides() {
        BlendModeStorage.restoreBlend();
        DepthColorStorage.unlockDepthColor();
        GLStateManager.getBlendMode().setVanillaLayer(null);
        GLStateManager.getBlendState().setVanillaLayer(null);
        GLStateManager.getDepthState().setVanillaLayer(null);
    }

    @Test
    void sampleUsesVanillaStateHiddenByPackOverrides() {
        GLStateManager.getBlendMode().setVanillaLayer(BlendModeStorage.ENABLE_LAYER);
        GLStateManager.getBlendState().setVanillaLayer(BlendModeStorage.FUNC_LAYER);
        GLStateManager.getDepthState().setVanillaLayer(DepthColorStorage.DEPTH_LAYER);

        GLStateManager.enableBlend();
        GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glDepthMask(true);

        final BlendState packBlend = new BlendState();
        packBlend.setAll(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        BlendModeStorage.overrideBlend(packBlend);
        DepthColorStorage.disableDepthColor();

        final ParticleRenderState sampled = new ParticleRenderState();
        sampled.sample();

        assertTrue(sampled.blend);
        assertEquals(GL11.GL_SRC_ALPHA, sampled.blendSrc);
        assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, sampled.blendDst);
        assertEquals(GL11.GL_ONE, sampled.blendSrcAlpha);
        assertEquals(GL11.GL_ZERO, sampled.blendDstAlpha);
        assertTrue(sampled.depthMask);
        assertFalse(GLStateManager.getDepthState().isEnabled(), "the pack override remains active at the raw layer");
    }
}
