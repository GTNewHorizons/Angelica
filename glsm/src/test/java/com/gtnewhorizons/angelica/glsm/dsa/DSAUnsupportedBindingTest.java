package com.gtnewhorizons.angelica.glsm.dsa;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class DSAUnsupportedBindingTest {

    private final DSAUnsupported dsa = new DSAUnsupported();

    @Test
    void nonTwoDParameterEditsRestoreTheTargetBinding() {
        final int a = GLStateManager.glGenTextures();
        final int c = GL11.glGenTextures();
        final int d = GL11.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, a);
            for (int tex : new int[] { c, d }) {
                GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, tex);
                for (int i = 0; i < 6; i++) {
                    GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                }
            }

            dsa.texParameteri(c, GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            assertEquals(GL11.GL_NEAREST, dsa.getTexParameteri(c, GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER));
            assertEquals(d, GL11.glGetInteger(GL13.GL_TEXTURE_BINDING_CUBE_MAP));
            assertEquals(a, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        } finally {
            GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
            GL11.glDeleteTextures(c);
            GL11.glDeleteTextures(d);
            GLStateManager.glDeleteTextures(a);
        }
    }

    @Test
    void twoDParameterEditRestoresTheCachedBinding() {
        final int a = GLStateManager.glGenTextures();
        final int b = GLStateManager.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, a);
            dsa.texParameteri(b, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            assertEquals(a, GLStateManager.getBoundTextureForServerState());
            assertEquals(a, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, b);
            assertEquals(GL11.GL_NEAREST, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, a);
        } finally {
            GLStateManager.glDeleteTextures(a);
            GLStateManager.glDeleteTextures(b);
        }
    }

    @Test
    void twoDEditWithANonTwoDTextureBoundRestoresTheDriverTwoDBinding() {
        final int y = GLStateManager.glGenTextures();
        final int t = GLStateManager.glGenTextures();
        final int x = GL11.glGenTextures();
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE3);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, t);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, y);
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, x);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            }

            dsa.texParameteri(t, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            assertEquals(y, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            assertEquals(x, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D));
            assertEquals(y, GLStateManager.getBoundTextureForServerState());

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
            assertEquals(GL11.GL_NEAREST, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, y);
        } finally {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDeleteTextures(y);
            GLStateManager.glDeleteTextures(t);
            GL11.glDeleteTextures(x);
        }
    }
}
