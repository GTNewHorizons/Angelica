package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.GlyphData;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

public final class FontProviderSGA extends FontTextureArray {

    public static FontProviderSGA create() {
        final int size = getBoundTextureSize();
        return new FontProviderSGA(size, 1, new int[] {0}, GL11.GL_NEAREST);
    }

    private FontProviderSGA(int size, int layers, int[] layersLookupArray, int filter) {
        super(size, layers, layersLookupArray, filter);
        loadAtlas(0);
    }

    @Override
    public ByteBuffer generateGlyphData(int atlasId, GlyphData[] glyphs) {
        if (atlasId == 0) {
            final int[] charWidth = BatchingFontRenderer.sgaCharWidths;
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

            return getBoundTextureData(this.textureSize);
        }
        return null;
    }
}
