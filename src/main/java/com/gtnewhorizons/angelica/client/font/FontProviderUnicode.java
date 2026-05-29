package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

public final class FontProviderUnicode implements FontProvider {

    private FontProviderUnicode() {
        try {
            InputStream inputstream = Minecraft.getMinecraft().getResourceManager()
                .getResource(new ResourceLocation("font/glyph_sizes.bin")).getInputStream();
            //noinspection ResultOfMethodCallIgnored
            inputstream.read(this.glyphWidth);
        }
        catch (IOException ioexception) {
            throw new RuntimeException(ioexception);
        }
    }

    private static class InstLoader { static final FontProviderUnicode instance = new FontProviderUnicode(); }
    public static FontProviderUnicode get() { return FontProviderUnicode.InstLoader.instance; }

    private final byte[] glyphWidth = new byte[65536];

    @Override
    public boolean isGlyphAvailable(char chr) {
        return true;
    }

    @Override
    public char getRandomReplacement(char chr) {
        return chr;
    }

    @Override
    public float getUStart(char chr) {
        final float startColumnF = (float)((this.glyphWidth[chr] >>> 4) & 15);
        return ((float) (chr % 16 * 16) + startColumnF + 0.21f) / 256.0f;
    }

    @Override
    public float getVStart(char chr) {
        return ((float) ((chr & 255) / 16 * 16) + 0.21f) / 256.0f;
    }

    @Override
    public float getXAdvance(char chr) {
        final int startColumn = (this.glyphWidth[chr] >>> 4) & 15;
        final int endColumn = this.glyphWidth[chr] & 15;
        final float startColumnF = (float) startColumn;
        final float endColumnF = (float) (endColumn + 1);
        return (endColumnF - startColumnF) / 2.0F + 1.0F;
    }

    @Override
    public float getGlyphW(char chr) {
        final int startColumn = (this.glyphWidth[chr] >>> 4) & 15;
        final int endColumn = this.glyphWidth[chr] & 15;
        final float startColumnF = (float) startColumn;
        final float endColumnF = (float) (endColumn + 1);
        final float chrWidth = endColumnF - startColumnF - 0.02F;
        return chrWidth / 2.0f + 1.0f;
    }

    @Override
    public float getUSize(char chr) {
        final int startColumn = (this.glyphWidth[chr] >>> 4) & 15;
        final int endColumn = this.glyphWidth[chr] & 15;
        final float startColumnF = (float) startColumn;
        final float endColumnF = (float) (endColumn + 1);
        final float chrWidth = endColumnF - startColumnF - 0.02F;
        return (chrWidth - 0.42f) / 256.0f;
    }

    @Override
    public float getVSize(char chr) {
        return (16.0f - 0.42f) / 256.0f;
    }

    @Override
    public float getShadowOffset() {
        return FontConfig.fontShadowOffset * FontConfig.fontShadowOffsetUC;
    }

    @Override
    public float getYScaleMultiplier() {
        return 1;
    }
}
