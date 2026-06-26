package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.GlyphData;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

public final class FontProviderSGA extends FontTextureArray {

    public static FontProviderSGA create() {
        final int size = getBoundTextureSize();
        return new FontProviderSGA(size, 1, new short[] {0}, GL11.GL_NEAREST);
    }

    private FontProviderSGA(int size, int layers, short[] layersLookupArray, int filter) {
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

            return getBoundTextureData(this.textureSize);
        }
        return null;
    }
}
