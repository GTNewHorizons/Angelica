package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.*;

@GLCompatTest
public class GLSM_MaxBoundTextureUnit_UnitTest {

    @Test
    void testHighWaterMarkTracksNonZeroBindsOnly() {
        final int texId = GL11.glGenTextures();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE5);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            assertTrue(GLSMTextures.getMaxBoundTextureUnit() >= 5);

            final int before = GLSMTextures.getMaxBoundTextureUnit();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + before + 3);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            assertEquals(before, GLSMTextures.getMaxBoundTextureUnit(), "binding 0 must not raise the mark");

            GLSMTextures.trackMaxBoundTextureUnit(before + 7);
            assertEquals(before + 7, GLSMTextures.getMaxBoundTextureUnit());
            GLSMTextures.trackMaxBoundTextureUnit(0);
            assertEquals(before + 7, GLSMTextures.getMaxBoundTextureUnit(), "mark is monotonic");
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE5);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDeleteTextures(texId);
        }
    }
}
