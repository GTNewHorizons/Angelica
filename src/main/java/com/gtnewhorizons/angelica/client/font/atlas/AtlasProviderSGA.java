package com.gtnewhorizons.angelica.client.font.atlas;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.GlyphData;

import java.nio.ByteBuffer;

public final class AtlasProviderSGA implements AtlasProvider {

    @Override
    public ByteBuffer generateGlyphData(int atlasId, int textureSize, GlyphData[] glyphs) {
        if (atlasId == 0) {
            final int size = AtlasProvider.getBoundTextureSize();
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

            return AtlasProvider.getBoundTextureData(size);
        }

        return null;
    }
}
