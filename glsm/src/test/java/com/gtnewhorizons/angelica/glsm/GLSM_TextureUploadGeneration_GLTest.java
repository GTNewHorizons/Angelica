package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@GLCoreTest
public class GLSM_TextureUploadGeneration_GLTest {

    private static int allocate(int size) {
        final int texId = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(size * size * 4));
        return texId;
    }

    @Test
    void anUploadLeavesANonZeroGeneration() {
        final int texId = allocate(4);
        try {
            assertNotEquals(0, TextureInfoCache.INSTANCE.getInfo(texId).getUploadGeneration());
        } finally {
            GLStateManager.glDeleteTextures(texId);
        }
    }

    @Test
    void recreatingATextureAtAReusedIdYieldsAFreshInfo() {
        final int texId = allocate(4);
        try {
            final TextureInfo first = TextureInfoCache.INSTANCE.getInfo(texId);
            final int firstGeneration = first.getUploadGeneration();

            TextureInfoCache.INSTANCE.onDeleteTexture(texId);
            final ByteBuffer pixels = BufferUtils.createByteBuffer(4 * 4 * 4);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 4, 4, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            final TextureInfo second = TextureInfoCache.INSTANCE.getInfo(texId);
            assertNotSame(first, second, "a deleted id drops its entry, so the replacement must be a distinct instance");
            assertEquals(firstGeneration, second.getUploadGeneration(), "generations alias across a delete/recreate cycle, which is exactly why identity has to carry the distinction");
        } finally {
            GLStateManager.glDeleteTextures(texId);
        }
    }

    @Test
    void anInPlaceRewriteBumpsTheGeneration() {
        final int texId = allocate(4);
        try {
            final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texId);
            final int afterAlloc = info.getUploadGeneration();

            GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 2, 2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(2 * 2 * 4));
            assertNotEquals(afterAlloc, info.getUploadGeneration(), "content changed, so anything caching the pixels is stale");

            final int afterSub = info.getUploadGeneration();
            GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 1, 0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(4));
            assertEquals(afterSub, info.getUploadGeneration(), "only level 0 is tracked");
        } finally {
            GLStateManager.glDeleteTextures(texId);
        }
    }
}
