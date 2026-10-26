package com.gtnewhorizons.angelica.glsm.dsa;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.drainErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class DSAUnsupportedBindingTest {

    private final DSAUnsupported dsa = new DSAUnsupported();

    @Test
    void nonTwoDParameterEditsRestoreTheTargetBinding() {
        final int a = GLSMTextures.glGenTextures();
        final int c = GL11.glGenTextures();
        final int d = GL11.glGenTextures();
        try {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, a);
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
            GLSMTextures.glDeleteTextures(a);
        }
    }

    @Test
    void twoDParameterEditRestoresTheCachedBinding() {
        final int a = GLSMTextures.glGenTextures();
        final int b = GLSMTextures.glGenTextures();
        try {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, a);
            dsa.texParameteri(b, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            assertEquals(a, GLSMTextures.getBoundTextureForServerState());
            assertEquals(a, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, b);
            assertEquals(GL11.GL_NEAREST, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, a);
        } finally {
            GLSMTextures.glDeleteTextures(a);
            GLSMTextures.glDeleteTextures(b);
        }
    }

    @Test
    void twoDEditWithANonTwoDTextureBoundRestoresTheDriverTwoDBinding() {
        final int y = GLSMTextures.glGenTextures();
        final int t = GLSMTextures.glGenTextures();
        final int x = GL11.glGenTextures();
        try {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE3);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, t);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, y);
            GLSMTextures.glBindTexture(GL12.GL_TEXTURE_3D, x);
            drainErrors();

            dsa.texParameteri(t, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            assertEquals(y, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            assertEquals(x, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D));
            assertEquals(y, GLSMTextures.getBoundTextureForServerState());

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
            assertEquals(GL11.GL_NEAREST, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, y);
        } finally {
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glDeleteTextures(y);
            GLSMTextures.glDeleteTextures(t);
            GL11.glDeleteTextures(x);
        }
    }
}
