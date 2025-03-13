package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.OpenGLTestBase;
import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33C;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GLSM_TextureManager_UnitTest extends OpenGLTestBase {

    private List<Integer> allocatedTextures;

    @BeforeEach
    void setUp() {
        // Reset to texture unit 0
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
        allocatedTextures = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        // Clean up all allocated textures
        for (int texture : allocatedTextures) {
            GLTextureManager.glDeleteTextures(texture);
        }
        allocatedTextures.clear();

        // Reset to texture unit 0
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
    }

    /**
     * Creates a texture and adds it to the tracking list for cleanup
     *
     * @return The texture ID
     */
    private int createTexture() {
        final int texture = GL33C.glGenTextures();
        allocatedTextures.add(texture);
        return texture;
    }

    /**
     * Sets up a basic texture for testing
     *
     * @param texture The texture ID
     */
    private void setupTexture(int texture) {
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture);

        // 2x2 RGBA texture
        final ByteBuffer pixels = BufferUtils.createByteBuffer(16);
        for (int i = 0; i < 16; i++) {
            pixels.put((byte) (i % 4 == 3 ? 255 : i % 255));
        }
        pixels.flip();

        GLTextureManager.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, 2, 2, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixels);
    }

    @Test
    void testTextureBinding() {
        // Verify initial state
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Initial texture binding should be 0");

        // Create and bind a texture
        final int texture1 = createTexture();
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture1, "Texture binding should be updated");

        // Binding the same texture again should be cached
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture1, "Texture binding should still be the same");

        // Create and bind another texture
        final int texture2 = createTexture();
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture2);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture2, "Texture binding should be updated to the new texture");

        // Bind texture1 again
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture1, "Texture binding should be updated back to the first texture");

        // Bind texture 0 (unbind)
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, 0);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture binding should be reset to 0");
    }

    @Test
    void testMultipleTextureUnits() {
        // Create textures
        final int texture1 = createTexture();
        final int texture2 = createTexture();

        // Verify initial state - texture unit 0 with no binding
        verifyState(GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE0, "Initial active texture unit should be 0");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Initial texture binding should be 0");

        // Bind texture1 to unit 0
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture1, "Texture1 should be bound to unit 0");

        // Switch to texture unit 1
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE1);
        verifyState(GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE1, "Active texture unit should be 1");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Unit 1 should have no texture bound initially");

        // Bind texture2 to unit 1
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture2);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture2, "Texture2 should be bound to unit 1");

        // Switch back to unit 0 and verify texture1 is still bound
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
        verifyState(GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE0, "Active texture unit should be 0 again");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture1, "Texture1 should still be bound to unit 0");

        // Verify that binding the same texture unit does nothing
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
        verifyState(GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE0, "Active texture unit should remain 0");

        // Verify that binding a different unit changes the state
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE1);
        verifyState(GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE1, "Active texture unit should be 1 again");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture2, "Texture2 should still be bound to unit 1");
    }

    @Test
    void testActiveTextureARB() {
        // This test checks the ARB variant of glActiveTexture which is also supported

        // Create textures
        final int texture1 = createTexture();
        final int texture2 = createTexture();

        // Verify initial state - texture unit 0 with no binding
        verifyState(GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE0, "Initial active texture unit should be 0");

        // Bind texture1 to unit 0
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture1);

        // Switch to texture unit 1 using ARB variant
        GLTextureManager.glActiveTextureARB(GL33C.GL_TEXTURE1);
        verifyState(GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE1, "Active texture unit should be 1");

        // Bind texture2 to unit 1
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture2);

        // Switch back to unit 0 using ARB variant
        GLTextureManager.glActiveTextureARB(GL33C.GL_TEXTURE0);
        verifyState(GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE0, "Active texture unit should be 0 again");
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture1, "Texture1 should still be bound to unit 0");
    }

    @Test
    void testTextureParameteri() {
        // Create and set up a texture
        final int texture = createTexture();
        setupTexture(texture);

        // Test setting various integer texture parameters
        GLTextureManager.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
        assertEquals(
            GL33C.GL_NEAREST,
            GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER),
            "Texture min filter should be updated");

        // Setting the same parameter twice should be cached and have no effect
        GLTextureManager.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
        assertEquals(
            GL33C.GL_NEAREST,
            GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER),
            "Texture min filter should remain the same");

        // Test other parameters
        GLTextureManager.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
        assertEquals(
            GL33C.GL_NEAREST,
            GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER),
            "Texture mag filter should be updated");

        GLTextureManager.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        assertEquals(GL11.GL_CLAMP, GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S), "Texture wrap S should be updated");

        GLTextureManager.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        assertEquals(GL11.GL_CLAMP, GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T), "Texture wrap T should be updated");

        GLTextureManager.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAX_LEVEL, 5);
        assertEquals(5, GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAX_LEVEL), "Texture max level should be updated");

        GLTextureManager.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_LOD, -3);
        assertEquals(-3, GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_LOD), "Texture min LOD should be updated");

        GLTextureManager.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAX_LOD, 3);
        assertEquals(3, GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAX_LOD), "Texture max LOD should be updated");
    }

    @Test
    void testTextureParameterf() {
        // Create and set up a texture
        final int texture = createTexture();
        setupTexture(texture);

        // Test setting float texture parameters
        GLTextureManager.glTexParameterf(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_LOD_BIAS, 1.5f);
        assertEquals(1.5f, GLTextureManager.glGetTexParameterf(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_LOD_BIAS), 0.0001f, "Texture LOD bias should be updated");

        // Setting the same parameter twice should be cached
        GLTextureManager.glTexParameterf(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_LOD_BIAS, 1.5f);
        assertEquals(
            1.5f,
            GLTextureManager.glGetTexParameterf(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_LOD_BIAS),
            0.0001f,
            "Texture LOD bias should remain the same");

        // Test anisotropic filtering parameter if supported
        try {
            final float maxAnisotropy = 4.0f;
            GLTextureManager.glTexParameterf(GL33C.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, maxAnisotropy);
            assertEquals(
                maxAnisotropy,
                GLTextureManager.glGetTexParameterf(GL33C.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT),
                0.0001f,
                "Texture max anisotropy should be updated");
        } catch (Exception e) {
            // Anisotropic filtering might not be supported on all platforms
            System.out.println("Anisotropic filtering not supported: " + e.getMessage());
        }
    }

    @Test
    void testTextureParameteriv() {
        // Create and set up a texture
        final int texture = createTexture();
        setupTexture(texture);

        // Test setting texture parameters with IntBuffer
        // LWJGL requires at least 4 elements in the buffer
        final IntBuffer params = BufferUtils.createIntBuffer(4);
        params.put(GL33C.GL_NEAREST).put(0).put(0).put(0).flip();

        GLTextureManager.glTexParameteriv(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, params);
        assertEquals(
            GL33C.GL_NEAREST,
            GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER),
            "Texture min filter should be updated with IntBuffer");

        // Setting the same parameter twice should be cached
        params.rewind();
        GLTextureManager.glTexParameteriv(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, params);
        assertEquals(
            GL33C.GL_NEAREST,
            GLTextureManager.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER),
            "Texture min filter should remain the same");
    }

    @Test
    void testTextureParameterfv() {
        // Create and set up a texture
        final int texture = createTexture();
        setupTexture(texture);

        // Test setting texture parameters with FloatBuffer
        // LWJGL requires at least 4 elements in the buffer
        final FloatBuffer params = BufferUtils.createFloatBuffer(4);
        params.put(1.5f).put(0f).put(0f).put(0f).flip();

        GLTextureManager.glTexParameterfv(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_LOD_BIAS, params);
        assertEquals(
            1.5f,
            GLTextureManager.glGetTexParameterf(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_LOD_BIAS),
            0.0001f,
            "Texture LOD bias should be updated with FloatBuffer");

        // Setting the same parameter twice should be cached
        params.rewind();
        GLTextureManager.glTexParameterfv(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_LOD_BIAS, params);
        assertEquals(
            1.5f,
            GLTextureManager.glGetTexParameterf(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_LOD_BIAS),
            0.0001f,
            "Texture LOD bias should remain the same");
    }

    @Test
    void testTextureLevelParameters() {
        // Create and set up a texture
        final int texture = createTexture();
        setupTexture(texture);

        // Test getting texture level parameters
        assertEquals(2, GLTextureManager.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_WIDTH), "Texture width should be 2");
        assertEquals(2, GLTextureManager.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_HEIGHT), "Texture height should be 2");
        assertEquals(
            GL33C.GL_RGBA,
            GLTextureManager.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_INTERNAL_FORMAT),
            "Texture internal format should be GL_RGBA");
    }

    @Test
    void testTextureDeletion() {
        // Create and set up textures
        final int texture1 = createTexture();
        final int texture2 = createTexture();

        setupTexture(texture1);
        setupTexture(texture2);

        // Bind texture1 to texture unit 0
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture1, "Texture1 should be bound to unit 0");

        // Bind texture2 to texture unit 1
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE1);
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture2);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture2, "Texture2 should be bound to unit 1");

        // Delete texture1 - should be unbound from unit 0
        GLTextureManager.glDeleteTextures(texture1);

        // Check that it's been unbound from unit 0
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture1 should be unbound from unit 0 after deletion");

        // Texture2 should still be bound to unit 1
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture2, "Texture2 should still be bound to unit 1");

        // Remove texture1 from the tracked list since we manually deleted it
        allocatedTextures.remove(Integer.valueOf(texture1));
    }

    @Test
    void testTextureDeletionWithBuffer() {
        // Create and set up multiple textures
        final int texture1 = createTexture();
        final int texture2 = createTexture();
        final int texture3 = createTexture();

        setupTexture(texture1);
        setupTexture(texture2);
        setupTexture(texture3);

        // Bind textures to different units
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture1);

        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE1);
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture2);

        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE2);
        GLTextureManager.glBindTexture(GL33C.GL_TEXTURE_2D, texture3);

        // Delete multiple textures using an IntBuffer
        final IntBuffer texturesToDelete = BufferUtils.createIntBuffer(2);
        texturesToDelete.put(texture1).put(texture2).flip();

        GLTextureManager.glDeleteTextures(texturesToDelete);

        // Verify texture1 was unbound from unit 0
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture1 should be unbound from unit 0 after buffer deletion");

        // Verify texture2 was unbound from unit 1
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE1);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, 0, "Texture2 should be unbound from unit 1 after buffer deletion");

        // Texture3 should still be bound to unit 2
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE2);
        verifyState(GL11.GL_TEXTURE_BINDING_2D, texture3, "Texture3 should still be bound to unit 2");

        // Remove manually deleted textures from the tracking list
        allocatedTextures.remove(Integer.valueOf(texture1));
        allocatedTextures.remove(Integer.valueOf(texture2));
    }

    @Test
    void testEnableDisableTexture2D() {
        // Test the enable/disable texture 2D functions

        // Initially texture 2D should be disabled
        verifyState(GL33C.GL_TEXTURE_2D, false, "Texture 2D should initially be disabled");

        // Enable texture 2D
        GLStateManager.enableTexture2D();
        verifyState(GL33C.GL_TEXTURE_2D, true, "Texture 2D should be enabled");

        // Disable texture 2D
        GLStateManager.disableTexture2D();
        verifyState(GL33C.GL_TEXTURE_2D, false, "Texture 2D should be disabled again");

        // Check with multiple texture units
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE1);
        GLStateManager.enableTexture2D();
        verifyState(GL33C.GL_TEXTURE_2D, true, "Texture 2D should be enabled on unit 1");

        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE0);
        verifyState(GL33C.GL_TEXTURE_2D, false, "Texture 2D should still be disabled on unit 0");

        // Clean up
        GLTextureManager.glActiveTexture(GL33C.GL_TEXTURE1);
        GLStateManager.disableTexture2D();
    }
}
