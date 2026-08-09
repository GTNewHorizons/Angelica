package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.states.ImageUnitBinding;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_ImageUnitBindingCache_UnitTest {

    private static void drainErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    private static void unbind(int unit) {
        GLStateManager.glBindImageTexture(unit, 0, 0, false, 0, GL15.GL_READ_ONLY, 0);
    }

    @Test
    void bindThenQueryReturnsFullTuple() {
        drainErrors();
        final int tex = GL11.glGenTextures();
        try {
            GLStateManager.glBindImageTexture(2, tex, 1, false, 3, GL15.GL_READ_ONLY, GL30.GL_RGBA32UI);

            assertEquals(tex, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 2));
            assertEquals(1, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 2));
            assertEquals(GL11.GL_FALSE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2));
            assertEquals(3, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2));
            assertEquals(GL15.GL_READ_ONLY, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 2));
            assertEquals(GL30.GL_RGBA32UI, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 2));
        } finally {
            unbind(2);
            GLStateManager.glDeleteTextures(tex);
            drainErrors();
        }
    }

    @Test
    void saveRestoreRoundTripsTheWholeTuple() {
        drainErrors();
        final int a = GL11.glGenTextures();
        final int b = GL11.glGenTextures();
        try {
            GLStateManager.glBindImageTexture(0, a, 2, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            final ImageUnitBinding live = GLStateManager.getImageUnitBinding(0);
            assertNotNull(live);
            final ImageUnitBinding saved = live.copy();

            GLStateManager.glBindImageTexture(0, b, 0, true, 0, GL15.GL_WRITE_ONLY, 0);
            assertEquals(b, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));

            GLStateManager.glBindImageTexture(0, saved.getTexture(), saved.getLevel(), saved.isLayered(), saved.getLayer(), saved.getAccess(), saved.getFormat());

            assertTrue(saved.sameAs(GLStateManager.getImageUnitBinding(0)), "restore must reproduce every field");
            assertEquals(a, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(2, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 0));
            assertEquals(GL15.GL_READ_WRITE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 0));
        } finally {
            unbind(0);
            GLStateManager.glDeleteTextures(a);
            GLStateManager.glDeleteTextures(b);
            drainErrors();
        }
    }

    @Test
    void resetToZeroIsNotARestore() {
        drainErrors();
        final int tex = GL11.glGenTextures();
        try {
            GLStateManager.glBindImageTexture(0, tex, 1, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            GLStateManager.glBindImageTexture(0, 0, 0, true, 0, GL15.GL_WRITE_ONLY, 0);

            assertEquals(0, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(0, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 0));
        } finally {
            unbind(0);
            GLStateManager.glDeleteTextures(tex);
            drainErrors();
        }
    }

    @Test
    void deletingTheBoundTextureClearsTheImageUnit() {
        drainErrors();
        final int tex = GL11.glGenTextures();
        try {
            GLStateManager.glBindImageTexture(1, tex, 0, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            assertEquals(tex, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1));

            GLStateManager.glDeleteTextures(tex);
            assertEquals(0, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1), "GL unbinds a deleted texture from every image unit");
        } finally {
            unbind(1);
            drainErrors();
        }
    }

    @Test
    void unitsAreIndependentAndUnitZeroIsNotSpecial() {
        drainErrors();
        final int a = GL11.glGenTextures();
        final int b = GL11.glGenTextures();
        try {
            GLStateManager.glBindImageTexture(0, a, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            GLStateManager.glBindImageTexture(3, b, 1, false, 2, GL15.GL_WRITE_ONLY, GL30.GL_RGBA32UI);

            assertEquals(a, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(b, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 3));
            assertEquals(GL11.GL_TRUE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 0));
            assertEquals(GL11.GL_FALSE, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 3));
        } finally {
            unbind(0);
            unbind(3);
            GLStateManager.glDeleteTextures(a);
            GLStateManager.glDeleteTextures(b);
            drainErrors();
        }
    }

    @Test
    void outOfRangeUnitDoesNotCorruptNeighbours() {
        drainErrors();
        final int tex = GL11.glGenTextures();
        try {
            GLStateManager.glBindImageTexture(0, tex, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            final int beyond = GLStateManager.getImageUnitBinding(0) == null ? 0 : Integer.MAX_VALUE - 1;
            assertFalse(beyond == 0, "unit 0 must resolve for this test to mean anything");

            GLStateManager.glBindImageTexture(beyond, tex, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            assertEquals(tex, GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
        } finally {
            unbind(0);
            GLStateManager.glDeleteTextures(tex);
            drainErrors();
        }
    }

    @Test
    void driverAgreesWhenAvailable() {
        assumeTrue(GLStateManager.capabilities != null && GLStateManager.capabilities.OpenGL42, "no GL 4.2 image load/store on this context");
        drainErrors();
        final int tex = GL11.glGenTextures();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_R32UI, 4, 4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            GLStateManager.glBindImageTexture(1, tex, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            drainErrors();

            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1), GLStateManager.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1));
        } finally {
            unbind(1);
            GLStateManager.glDeleteTextures(tex);
            drainErrors();
        }
    }
}
