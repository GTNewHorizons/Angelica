package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.GlyphData;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

public final class FontProviderMinecraft extends FontTextureArray {

    public static FontProviderMinecraft create() {
        int layer = 0;
        final int[] layersLookupArray = new int[256];
        for (int i = 0; i < 256; i++) {
//                try {
//                    //Minecraft.getMinecraft().getResourceManager().getResource(getUnicodePage(i));
//                } catch (Exception ignored) {
//                    System.out.println("Could not find Layer " + i + ".");
//                } TODO resourceExists
            layersLookupArray[i] = layer;
            layer++;
        }
        return new FontProviderMinecraft(256, layer, layersLookupArray, GL11.GL_NEAREST);
    }


    private FontProviderMinecraft(int size, int layers, int[] layersLookupArray, int filter) {
        super(size, layers, layersLookupArray, filter);
        loadAtlas(0); // Load first texture from bound texture atlas
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
                final float xAdvance = charWidth[index];
                final float glyphW = charWidth[index] - 0.01F;
                final float uSize = (charWidth[index] - 1.01F) / 256.0F;
                final float vSize = 7.99F / 256.0F;

                glyphs[i] = new GlyphData(
                    uStart, vStart,
                    xAdvance,
                    glyphW,
                    uSize,
                    vSize
                );
            }

            return getBoundTextureData(size);
        }

        final byte[] glyphWidth = BatchingFontRenderer.glyphWidths;
        for (int i = 0; i < 256; i++) {
            final char chr = (char) ((atlasId * 256) | i);
            final int startColumn = ((glyphWidth[chr] >>> 4) & 15);
            final int endColumn = glyphWidth[chr] & 15;
            final float chrWidth = endColumn - startColumn - 0.02F;
            final float uStart = ((chr % 16 * 16) + startColumn + 0.21f) / 256.0f;
            final float vStart = ((float) ((chr & 255) / 16 * 16) + 0.21f) / 256.0f;
            final float xAdvance = (endColumn - startColumn) / 2.0F + 1.0F;
            final float glyphW = chrWidth / 2.0f + 1.0f;
            final float uSize = (chrWidth - 0.42f) / 256.0f;
            final float vSize = (16.0f - 0.42f) / 256.0f;
            glyphs[i] = new GlyphData(
                uStart, vStart,
                xAdvance,
                glyphW,
                uSize,
                vSize
            );
        }

        final ResourceLocation resource = new ResourceLocation(String.format("textures/font/unicode_page_%02x.png", atlasId));

        return getTextureFromResource(resource);
    }
}
