package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizon.gtnhlib.client.renderer.textures.TextureLoader;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.GlyphData;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

public final class FontProviderMinecraft extends FontTextureArray {

    public static final GlyphData[] unicodeGlyphData = new GlyphData[256];
    public static int unicodeLayer;

    public static FontProviderMinecraft create() {
        short layer = 0;
        final int amountLayers = 256 + 1; // 256 + 1 because of Unicode pages.
        final short[] layersLookupArray = new short[amountLayers];
        for (int i = 0; i < amountLayers; i++) {
            if (TextureLoader.resourceExists(getUnicodePage(i))) {
                layersLookupArray[i] = layer;
                layer++;
            } else {
                layersLookupArray[i] = -1;
            }
        }
        layersLookupArray[256] = layer;
        unicodeLayer = layer;
        System.out.println("Amount layers: " + layer);
        System.out.println(Arrays.toString(layersLookupArray));
        return new FontProviderMinecraft(256, layer + 1, layersLookupArray, GL11.GL_NEAREST);
    }


    private FontProviderMinecraft(int size, int layers, short[] layersLookupArray, int filter) {
        super(size, layers, layersLookupArray, filter);
        loadAtlas(0); // Load first texture from bound texture atlas

        final ResourceLocation resource = getUnicodePage(0);
        final ByteBuffer pixels = getTextureFromResource(resource);

        bind();

        if (pixels == null) {
            return;
        }
        final int textureSize = (int) Math.sqrt(pixels.capacity());
        GLStateManager.glTexSubImage3D(
            GL30.GL_TEXTURE_2D_ARRAY,
            0,
            0, 0, layers - 1,
            textureSize, textureSize,
            1,
            GL11.GL_RED,
            GL11.GL_UNSIGNED_BYTE,
            pixels
        );

        memFree(pixels);
    }

    @Override
    public ByteBuffer generateGlyphData(int atlasId, GlyphData[] glyphs) {
        if (atlasId == 0) {
            final int size = getBoundTextureSize();
            final int[] charWidth = BatchingFontRenderer.asciiCharWidths;
            for (int i = 0; i < 256; i++) {
                final int index = BatchingFontRenderer.lookupMcFontPosition((char) i);
                final float uStart = ((index & 15) * 8) / 256.0F;
                final float vStart = ((index >> 4) * 8) / 256.0F;
                final float glyphW = charWidth[index] - 1F;
                final float uSize = (charWidth[index] - 1F) / 256.0F;
                final float vSize = 7.99F / 256.0F;

                glyphs[i] = new GlyphData(
                    uStart, vStart,
                    glyphW,
                    uSize,
                    vSize
                );
            }

            populateUnicodeGlyphs(0, unicodeGlyphData);

            return getBoundTextureData(size);
        }

        populateUnicodeGlyphs(atlasId, glyphs);

        return getTextureFromResource(getUnicodePage(atlasId));
    }

    private void populateUnicodeGlyphs(int atlasId, GlyphData[] glyphs) {
        final byte[] glyphWidth = BatchingFontRenderer.glyphWidths;
        for (int i = 0; i < 256; i++) {
            final char chr = (char) ((atlasId * 256) | i);
            int row = glyphWidth[chr] >>> 4;
            int column = glyphWidth[chr] & 15;
            float uStart = ((float)(chr % 16 * 16) + row) / 256F;
            float vStart = ((float)((chr & 255) / 16 * 16)) / 256F;
            float uSize = ((column + 1) - row - 0.02F) / 256F;
            float vSize = 15.98F / 256.0F;
            float glyphW = ((column + 1) - row) / 2.0F + 1.0F;
            glyphs[i] = new GlyphData(
                uStart, vStart,
                glyphW - 1,
                uSize,
                vSize
            );
        }
    }

    private static ResourceLocation getUnicodePage(int atlasId) {
        return new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", atlasId));
    }
}
