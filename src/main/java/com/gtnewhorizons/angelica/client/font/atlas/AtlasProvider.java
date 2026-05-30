package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizon.gtnhlib.client.renderer.textures.TextureLoader;
import com.gtnewhorizons.angelica.client.font.GlyphData;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;

public interface AtlasProvider {

    ByteBuffer generateGlyphData(int atlasId, int textureSize, GlyphData[] glyphs);

    static ByteBuffer getBoundTextureData(final int size) {
        final ByteBuffer pixels = memAlloc(size * size);
        GLStateManager.glGetTexImage(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RED,
            GL11.GL_UNSIGNED_BYTE,
            pixels
        );
        return pixels;
    }

    static int getBoundTextureSize() {
        return GLStateManager.glGetTexLevelParameteri(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_TEXTURE_WIDTH
        );
    }

    static ByteBuffer getTextureFromResource(ResourceLocation resource) {
        try {
            final BufferedImage image = TextureLoader.getBufferedImage(Minecraft.getMinecraft().getResourceManager()
                .getResource(resource));
            if (image.getWidth() != image.getHeight()) {
                throw new UnsupportedOperationException(resource + "has a different width and height, they cannot get rendered!");
            }
            return getPixelBuffer(image);
        } catch (IOException e) {
            System.out.println("Failed to load resource " + resource + ".");
            return null;
        }
    }

    static ByteBuffer getPixelBuffer(BufferedImage image) {
        final int size = image.getWidth();
        final ByteBuffer pixelBuffer = memAlloc(size * size);
        pixelBuffer.clear();
        final int[] pixelValues = TextureLoader.getImagePixels(image);
        for (int i : pixelValues) {
            pixelBuffer.put((byte) ((i >> 24) & 0xFF)); // Only extract alpha
        }
        pixelBuffer.position(0).limit(pixelValues.length);
        return pixelBuffer;
    }
}
