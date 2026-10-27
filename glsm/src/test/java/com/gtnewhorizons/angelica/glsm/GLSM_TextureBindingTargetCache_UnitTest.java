package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class GLSM_TextureBindingTargetCache_UnitTest {

    @Test
    void flattenedCacheTracksNon2DTargets() {
        final int t3d = GL11.glGenTextures();
        final int t2d = GL11.glGenTextures();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE3);
            GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, t3d);
            assertEquals(t3d, GLSMTextures.getBoundTextureForServerState(3), "cache must track a 3D bind");
            assertEquals(t3d, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D), "3D bind must reach the driver");

            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, t2d);
            assertEquals(t2d, GLSMTextures.getBoundTextureForServerState(3), "cache must track the 2D bind after a 3D bind");
            assertEquals(t2d, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));

            GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, t3d);
            assertEquals(t3d, GLSMTextures.getBoundTextureForServerState(3), "cache must track the 3D re-bind");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glDeleteTextures(t3d);
            GLSMTextures.glDeleteTextures(t2d);
        }
    }

    @Test
    void proxyTargetBindIsNeitherForwardedNorCached() {
        final int t2d = GL11.glGenTextures();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE5);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, t2d);

            GLSMTextures.glBindTexture(GL11.GL_PROXY_TEXTURE_2D, 0);
            GLSMTextures.glBindTexture(GL12.GL_PROXY_TEXTURE_3D, t2d);

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "a proxy target must never reach the driver");
            assertEquals(t2d, GLSMTextures.getBoundTextureForServerState(5), "a proxy bind must not disturb the cache");
            assertEquals(t2d, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        } finally {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glDeleteTextures(t2d);
        }
    }

    @Test
    void popAttribRestoresRecordedTarget() {
        final int t3d = GL11.glGenTextures();
        final int t2d = GL11.glGenTextures();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
            GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, t3d);

            GLSMAttribStack.glPushAttrib(GL11.GL_TEXTURE_BIT);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, t2d);
            GLSMAttribStack.glPopAttrib();

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "restore must not re-bind a 3D texture as 2D");
            assertEquals(t3d, GLSMTextures.getBoundTextureForServerState(2), "cache must restore the pre-push binding");
            assertEquals(t3d, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D), "restore must use the recorded target");
        } finally {
            GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glDeleteTextures(t3d);
            GLSMTextures.glDeleteTextures(t2d);
        }
    }

    @Test
    void unbindIsNotElidedAcrossTargets() {
        final int t2d = GL11.glGenTextures();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE4);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, t2d);
            GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            assertEquals(0, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "2D unbind after a 3D unbind must reach the driver");
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glDeleteTextures(t2d);
        }
    }
}
