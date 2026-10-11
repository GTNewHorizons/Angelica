package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_DeferredTextureDelete_GLTest {

    private static int immutableTexture() {
        assumeTrue(GLStateManager.capabilities.OpenGL42, "no glTexStorage2D on this context");
        final int tex = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLStateManager.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_R8, 4, 4);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    @Test
    void deletingAnImmutableTextureRaisesNoError() {
        final int tex = immutableTexture();

        GLStateManager.glDeleteTextures(tex);

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void deletingARescuedImmutableTextureRaisesNoError() {
        final int tex = immutableTexture();
        GLStateManager.glDeleteTextures(tex);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        GLStateManager.glDeleteTextures(tex);

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void deletingA2DTextureWhileA3DTextureIsBoundLeavesNothingBoundTo2D() {
        final int t2d = GLStateManager.glGenTextures();
        final int t3d = GLStateManager.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, t2d);
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, t3d);

            GLStateManager.glDeleteTextures(t2d);

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            assertEquals(0, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "the deleted texture must not stay bound");
            assertEquals(t3d, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D));
        } finally {
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(t3d);
        }
    }
}
