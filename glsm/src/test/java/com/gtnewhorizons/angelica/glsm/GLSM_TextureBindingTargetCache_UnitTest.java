package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class GLSM_TextureBindingTargetCache_UnitTest {

    private static void drainErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    @Test
    void flattenedCacheTracksNon2DTargets() {
        drainErrors();
        final int t3d = GL11.glGenTextures();
        final int t2d = GL11.glGenTextures();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE3);
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, t3d);
            assertEquals(t3d, GLStateManager.getBoundTextureForServerState(3), "cache must track a 3D bind");
            assertEquals(t3d, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D), "3D bind must reach the driver");

            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, t2d);
            assertEquals(t2d, GLStateManager.getBoundTextureForServerState(3), "cache must track the 2D bind after a 3D bind");
            assertEquals(t2d, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));

            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, t3d);
            assertEquals(t3d, GLStateManager.getBoundTextureForServerState(3), "cache must track the 3D re-bind");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDeleteTextures(t3d);
            GLStateManager.glDeleteTextures(t2d);
            drainErrors();
        }
    }

    @Test
    void popAttribRestoresRecordedTarget() {
        drainErrors();
        final int t3d = GL11.glGenTextures();
        final int t2d = GL11.glGenTextures();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, t3d);

            GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, t2d);
            GLStateManager.glPopAttrib();

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "restore must not re-bind a 3D texture as 2D");
            assertEquals(t3d, GLStateManager.getBoundTextureForServerState(2), "cache must restore the pre-push binding");
            assertEquals(t3d, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D), "restore must use the recorded target");
        } finally {
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDeleteTextures(t3d);
            GLStateManager.glDeleteTextures(t2d);
            drainErrors();
        }
    }

    @Test
    void unbindIsNotElidedAcrossTargets() {
        drainErrors();
        final int t2d = GL11.glGenTextures();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE4);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, t2d);
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            assertEquals(0, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "2D unbind after a 3D unbind must reach the driver");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDeleteTextures(t2d);
            drainErrors();
        }
    }
}
