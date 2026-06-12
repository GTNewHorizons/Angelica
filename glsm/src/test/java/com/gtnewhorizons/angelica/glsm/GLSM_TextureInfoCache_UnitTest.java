package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(GLSMExtension.class)
public class GLSM_TextureInfoCache_UnitTest {

    @Test
    void testGenericCompressedUploadResolvesToSpecificFormat() {
        int texId = GL11.glGenTextures();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            ByteBuffer pixels = BufferUtils.createByteBuffer(64 * 64 * 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL13.GL_COMPRESSED_RGBA, 64, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            assertNotEquals(-1, format);
            assertNotEquals(GL30.GL_COMPRESSED_RED, format);
            assertNotEquals(GL30.GL_COMPRESSED_RG, format);
            assertNotEquals(GL13.GL_COMPRESSED_RGB, format);
            assertNotEquals(GL13.GL_COMPRESSED_RGBA, format);
            assertNotEquals(GL21.GL_COMPRESSED_SRGB, format);
            assertNotEquals(GL21.GL_COMPRESSED_SRGB_ALPHA, format);
        } finally {
            GL11.glDeleteTextures(texId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    @Test
    void testNonGenericUploadCachesWithoutRoundtrip() {
        int texId = GL11.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            ByteBuffer pixels = BufferUtils.createByteBuffer(64 * 64 * 4);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 64, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texId);
            assertFalse(info.needsInternalFormatResolve());
            assertEquals(GL11.GL_RGBA8, info.getResolvedInternalFormat());
            assertEquals(GL11.GL_RGBA8, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
        } finally {
            GL11.glDeleteTextures(texId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    @Test
    void testGenericCompressedPreservesAsPassedInternalFormat() {
        int texId = GL11.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            ByteBuffer pixels = BufferUtils.createByteBuffer(64 * 64 * 4);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL13.GL_COMPRESSED_RGBA, 64, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texId);
            assertEquals(GL13.GL_COMPRESSED_RGBA, info.getInternalFormat());
            assertTrue(info.needsInternalFormatResolve());
        } finally {
            GL11.glDeleteTextures(texId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    @Test
    void testRepeatedQueryAfterResolveUsesCache() {
        int texId = GL11.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            ByteBuffer pixels = BufferUtils.createByteBuffer(64 * 64 * 4);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL13.GL_COMPRESSED_RGBA, 64, 64, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texId);
            assertTrue(info.needsInternalFormatResolve());
            int first = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            assertFalse(info.needsInternalFormatResolve());
            int second = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            assertEquals(first, second);
        } finally {
            GL11.glDeleteTextures(texId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    @Test
    void testIsGenericCompressedInternalFormatHelper() {
        assertTrue(TextureInfoCache.isGenericCompressedInternalFormat(GL30.GL_COMPRESSED_RED));
        assertTrue(TextureInfoCache.isGenericCompressedInternalFormat(GL30.GL_COMPRESSED_RG));
        assertTrue(TextureInfoCache.isGenericCompressedInternalFormat(GL13.GL_COMPRESSED_RGB));
        assertTrue(TextureInfoCache.isGenericCompressedInternalFormat(GL13.GL_COMPRESSED_RGBA));
        assertTrue(TextureInfoCache.isGenericCompressedInternalFormat(GL21.GL_COMPRESSED_SRGB));
        assertTrue(TextureInfoCache.isGenericCompressedInternalFormat(GL21.GL_COMPRESSED_SRGB_ALPHA));
        assertFalse(TextureInfoCache.isGenericCompressedInternalFormat(GL11.GL_RGBA));
        assertFalse(TextureInfoCache.isGenericCompressedInternalFormat(GL11.GL_RGBA8));
        assertFalse(TextureInfoCache.isGenericCompressedInternalFormat(EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT));
        assertFalse(TextureInfoCache.isGenericCompressedInternalFormat(0));
        assertFalse(TextureInfoCache.isGenericCompressedInternalFormat(-1));
    }

    /**
     * Texture deletion is server-side; cache must be invalidated regardless of client caching state.
     */
    @Test
    void testCacheRemovedOnDeleteWhenCachingDisabled() throws Exception {
        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        ByteBuffer pixels = BufferUtils.createByteBuffer(4 * 4 * 4);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 4, 4, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

        TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texId);
        assertNotNull(info);

        // Disable caching (simulate SharedDrawable context)
        Field splashCompleteField = GLStateManager.class.getDeclaredField("splashComplete");
        splashCompleteField.setAccessible(true);
        boolean originalSplash = splashCompleteField.getBoolean(null);
        Thread originalHolder = GLStateManager.getDrawableGLHolder();

        try {
            splashCompleteField.setBoolean(null, false);
            GLStateManager.setDrawableGLHolder(null); // Current thread != null, so caching disabled
            assertFalse(GLStateManager.isCachingEnabled());

            GLStateManager.glDeleteTextures(texId);

            splashCompleteField.setBoolean(null, true);

            // Cache entry should be gone - we should get a fresh object
            assertNotSame(info, TextureInfoCache.INSTANCE.getInfo(texId));
        } finally {
            splashCompleteField.setBoolean(null, originalSplash);
            GLStateManager.setDrawableGLHolder(originalHolder);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }
}
