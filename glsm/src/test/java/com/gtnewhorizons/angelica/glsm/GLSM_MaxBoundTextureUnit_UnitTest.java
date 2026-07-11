package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(GLSMExtension.class)
public class GLSM_MaxBoundTextureUnit_UnitTest {

    @Test
    void testHighWaterMarkTracksNonZeroBindsOnly() {
        final int texId = GL11.glGenTextures();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE5);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            assertTrue(GLStateManager.getMaxBoundTextureUnit() >= 5);

            final int before = GLStateManager.getMaxBoundTextureUnit();
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + before + 3);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            assertEquals(before, GLStateManager.getMaxBoundTextureUnit(), "binding 0 must not raise the mark");

            GLStateManager.trackMaxBoundTextureUnit(before + 7);
            assertEquals(before + 7, GLStateManager.getMaxBoundTextureUnit());
            GLStateManager.trackMaxBoundTextureUnit(0);
            assertEquals(before + 7, GLStateManager.getMaxBoundTextureUnit(), "mark is monotonic");
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE5);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDeleteTextures(texId);
        }
    }
}
