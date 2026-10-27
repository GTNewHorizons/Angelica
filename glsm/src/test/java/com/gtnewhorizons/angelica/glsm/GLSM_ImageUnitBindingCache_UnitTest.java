package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.states.ImageUnitBinding;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.drainErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_ImageUnitBindingCache_UnitTest {

    private static void unbind(int unit) {
        GLSMTextures.glBindImageTexture(unit, 0, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R8);
    }

    @Test
    void bindThenQueryReturnsFullTuple() {
        final int tex = GL11.glGenTextures();
        try {
            GLSMTextures.glBindImageTexture(2, tex, 1, false, 3, GL15.GL_READ_ONLY, GL30.GL_RGBA32UI);

            assertEquals(tex, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 2));
            assertEquals(1, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 2));
            assertEquals(GL11.GL_FALSE, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2));
            assertEquals(3, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2));
            assertEquals(GL15.GL_READ_ONLY, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 2));
            assertEquals(GL30.GL_RGBA32UI, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 2));
        } finally {
            unbind(2);
            GLSMTextures.glDeleteTextures(tex);
        }
    }

    @Test
    void saveRestoreRoundTripsTheWholeTuple() {
        final int a = GL11.glGenTextures();
        final int b = GL11.glGenTextures();
        try {
            GLSMTextures.glBindImageTexture(0, a, 2, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            final ImageUnitBinding live = GLSMTextures.getImageUnitBinding(0);
            assertNotNull(live);
            final ImageUnitBinding saved = live.copy();

            GLSMTextures.glBindImageTexture(0, b, 0, true, 0, GL15.GL_WRITE_ONLY, GL30.GL_R8);
            assertEquals(b, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));

            GLSMTextures.glBindImageTexture(0, saved.getTexture(), saved.getLevel(), saved.isLayered(), saved.getLayer(), saved.getAccess(), saved.getFormat());

            assertTrue(saved.sameAs(GLSMTextures.getImageUnitBinding(0)), "restore must reproduce every field");
            assertEquals(a, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(2, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 0));
            assertEquals(GL15.GL_READ_WRITE, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 0));
        } finally {
            unbind(0);
            GLSMTextures.glDeleteTextures(a);
            GLSMTextures.glDeleteTextures(b);
        }
    }

    @Test
    void resetToZeroIsNotARestore() {
        final int tex = GL11.glGenTextures();
        try {
            GLSMTextures.glBindImageTexture(0, tex, 1, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            GLSMTextures.glBindImageTexture(0, 0, 0, true, 0, GL15.GL_WRITE_ONLY, GL30.GL_R8);

            assertEquals(0, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(0, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LEVEL, 0));
        } finally {
            unbind(0);
            GLSMTextures.glDeleteTextures(tex);
        }
    }

    @Test
    void deletingTheBoundTextureClearsTheImageUnit() {
        final boolean driverHasImages = GLStateManager.capabilities != null && GLStateManager.capabilities.OpenGL42;
        final int tex = GL11.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        try {
            GLSMTextures.glBindImageTexture(1, tex, 0, true, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);
            assertEquals(tex, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1));
            if (driverHasImages) assertEquals(tex, GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1));

            GLSMTextures.glDeleteTextures(tex);
            assertEquals(0, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1), "GL unbinds a deleted texture from every image unit");
            if (driverHasImages) assertEquals(0, GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1), "driver unbinds a deleted texture from every image unit");
            assertEquals(GL15.GL_READ_ONLY, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1));
            assertEquals(GL30.GL_R8, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1));
            if (driverHasImages) {
                assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1));
                assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1));
            }
        } finally {
            unbind(1);
        }
    }

    @Test
    void unitsAreIndependentAndUnitZeroIsNotSpecial() {
        final int a = GL11.glGenTextures();
        final int b = GL11.glGenTextures();
        try {
            GLSMTextures.glBindImageTexture(0, a, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            GLSMTextures.glBindImageTexture(3, b, 1, false, 2, GL15.GL_WRITE_ONLY, GL30.GL_RGBA32UI);

            assertEquals(a, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
            assertEquals(b, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 3));
            assertEquals(GL11.GL_TRUE, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 0));
            assertEquals(GL11.GL_FALSE, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 3));
        } finally {
            unbind(0);
            unbind(3);
            GLSMTextures.glDeleteTextures(a);
            GLSMTextures.glDeleteTextures(b);
        }
    }

    @Test
    void outOfRangeUnitDoesNotCorruptNeighbours() {
        final int tex = GL11.glGenTextures();
        try {
            GLSMTextures.glBindImageTexture(0, tex, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            final int beyond = GLSMTextures.getImageUnitBinding(0) == null ? 0 : Integer.MAX_VALUE - 1;
            assertFalse(beyond == 0, "unit 0 must resolve for this test to mean anything");

            GLSMTextures.glBindImageTexture(beyond, tex, 0, true, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            // Without image load/store the backend drops the call, so the driver never sees the unit.
            final ContextCapabilities caps = GLStateManager.capabilities;
            final boolean reachesDriver = caps.OpenGL42 || caps.GL_ARB_shader_image_load_store || caps.GL_EXT_shader_image_load_store;
            assertEquals(reachesDriver ? GL11.GL_INVALID_VALUE : GL11.GL_NO_ERROR, GL11.glGetError(), "driver must reject the out-of-range unit");
            assertEquals(tex, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 0));
        } finally {
            unbind(0);
            GLSMTextures.glDeleteTextures(tex);
        }
    }

    @Test
    void driverAgreesWhenAvailable() {
        assumeTrue(GLStateManager.capabilities != null && GLStateManager.capabilities.OpenGL42, "no GL 4.2 image load/store on this context");
        drainErrors();
        final int tex = GL11.glGenTextures();
        try {
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GLSMTextures.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_R32UI, 4, 4);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            GLSMTextures.glBindImageTexture(1, tex, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R32UI);
            drainErrors();

            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 1));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 1));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 1));

            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 3), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_NAME, 3));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 3), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_ACCESS, 3));
            assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 3), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_FORMAT, 3));
        } finally {
            unbind(1);
            GLSMTextures.glDeleteTextures(tex);
        }
    }

    @Test
    void bindingTextureZeroClearsLayeredAndLayer() {
        try {
            GLSMTextures.glBindImageTexture(2, 0, 0, true, 3, GL15.GL_READ_WRITE, GL11.GL_RGBA8);

            assertEquals(GL11.GL_FALSE, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2));
            assertEquals(0, GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2));
            if (GLStateManager.capabilities != null && GLStateManager.capabilities.OpenGL42) {
                assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LAYERED, 2));
                assertEquals(GL30.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2), GLSMQueries.glGetInteger(GL42.GL_IMAGE_BINDING_LAYER, 2));
            }
        } finally {
            unbind(2);
        }
    }
}
