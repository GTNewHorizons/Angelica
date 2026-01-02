package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AngelicaExtension.class)
public class GLSM_TextureInfoCache_UnitTest {

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
        boolean original = splashCompleteField.getBoolean(null);

        try {
            splashCompleteField.setBoolean(null, false);
            GLStateManager.setCachingEnabled(false);
            assertFalse(GLStateManager.isCachingEnabled());

            GLStateManager.glDeleteTextures(texId);

            splashCompleteField.setBoolean(null, true);

            // Cache entry should be gone - we should get a fresh object
            assertNotSame(info, TextureInfoCache.INSTANCE.getInfo(texId));
        } finally {
            splashCompleteField.setBoolean(null, original);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }
}
